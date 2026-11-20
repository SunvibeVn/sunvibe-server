package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.Furnishing;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.Pets;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The bounded working set of a revision command, the one-revision read and the versioned write on a real PostgreSQL
 * (listing-publish E06-S2 spec 5.3, E06S2-D14, E06S2-D19, E06S2-D20, plan E06S2-P7, E06S2-P16; RD-17, RD-18). Each
 * listing and its child rows are seeded first (testing.md). The annotations, autowired fields, {@code RANDOM},
 * {@code created} with its cleanup, and the helpers {@code listing}, {@code insert}, {@code insertRevision}, {@code tx},
 * {@code code} and {@code now} are copied as written from {@link ListingPersistencePostgresTest} (:51-82, :253-265,
 * :278-283, :294-304, plan E06S2-P1), so this class shares its context; the cleanup gains only the delete of the fee
 * rows, before their revisions. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingRevisionPersistencePostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String TITLE = "Phòng trọ mới gần chợ Hải Châu";
	private static final String DESCRIPTION = "Phòng sạch sẽ, có cửa sổ lớn.\n"
			.repeat(5);
	private static final RevisionFeatures MIXED = new RevisionFeatures(Furnishing.FULL, true, false, true,
			ShareMode.PRIVATE, ShareMode.SHARED, false, true, false, true, false, Pets.NOT_ALLOWED, true);
	private static final RevisionFeatures EVERY = new RevisionFeatures(Furnishing.BASIC, true, true, true,
			ShareMode.SHARED, ShareMode.PRIVATE, true, true, true, true, true, Pets.ALLOWED, true);

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@PersistenceContext
	private EntityManager entityManager;

	private final List<String> created = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		for (String id : created) {
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id IN"
					+ " (SELECT id FROM listing_revisions WHERE listing_id = ?)", id);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
	}

	@Test
	void findForCommand_workingSet_openAndTargetWithFees() {
		Listing listing = stored();
		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		IntStream.rangeClosed(2, 20).forEach(number -> insertRevision(listing.id(), number, "CANCELLED"));
		String draft = insertRow(listing.id(), 21, "DRAFT", merged(contentColumns(TITLE, DESCRIPTION), termsColumns()));
		insertFee(draft, 3, "Nước", "PROVIDER_TARIFF", null, null);
		insertFee(draft, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");
		insertFee(draft, 2, "Internet", "INCLUDED", null, null);

		Listing loaded = tx(() -> listings.findForCommand(listing.id(), 5)).orElseThrow();

		assertEquals(List.of(5, 21), loaded.revisions().stream().map(ListingRevision::revisionNo).toList());
		assertEquals(21, loaded.newestRevisionNo());
		assertEquals(0L, loaded.rowVersion());
		assertEquals(listing.agreementId(), loaded.agreementId());
		ListingRevision open = loaded.revision(21).orElseThrow();
		assertEquals(RevisionStatus.DRAFT, open.status());
		assertEquals(new RevisionContent(TITLE, DESCRIPTION, MIXED), open.content());
		assertEquals(List.of("Điện", "Internet", "Nước"),
				open.termsOrNull().fees().stream().map(RevisionTerms.Fee::name).toList());
		ListingRevision target = loaded.revision(5).orElseThrow();
		assertEquals(RevisionStatus.CANCELLED, target.status());
		assertEquals(RevisionContent.EMPTY, target.content());
		assertNull(target.termsOrNull());
	}

	@Test
	void findForCommand_noTarget_openOnly() {
		Listing listing = stored();
		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		insertRevision(listing.id(), 2, "DRAFT");

		Listing loaded = tx(() -> listings.findForCommand(listing.id(), null)).orElseThrow();

		assertEquals(List.of(2), loaded.revisions().stream().map(ListingRevision::revisionNo).toList());
		assertEquals(2, loaded.newestRevisionNo());
	}

	@Test
	void findForCommand_noOpenNoTarget_emptySetNewestNumber() {
		Listing listing = stored();
		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		insertRevision(listing.id(), 2, "CANCELLED");

		Listing loaded = tx(() -> listings.findForCommand(listing.id(), 9)).orElseThrow();

		assertTrue(loaded.revisions().isEmpty());
		assertEquals(2, loaded.newestRevisionNo());
		assertTrue(tx(() -> listings.findForCommand(ids.generateId(), null)).isEmpty());
	}

	@Test
	void findForCommand_feeRowsUnderRevisionWithoutTerms_ignored() {
		Listing listing = stored();
		String revision = listing.revisions().getFirst().id();
		insertFee(revision, 2, "Internet", "INCLUDED", null, null);
		insertFee(revision, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");

		ListingRevision withoutTerms = tx(() -> listings.findForCommand(listing.id(), null)).orElseThrow()
				.revision(1).orElseThrow();

		assertNull(withoutTerms.termsOrNull());
		jdbc.update("UPDATE listing_revisions SET terms_source = 'IN_EFFECT', terms_captured_at = ?,"
				+ " terms_monthly_rent = 8000000, terms_deposit_basis = 'MONTHS', terms_deposit_months = 2,"
				+ " terms_minimum_term_months = 6, terms_payment_period_months = 1 WHERE id = ?",
				Timestamp.from(now()), revision);
		ListingRevision withTerms = tx(() -> listings.findForCommand(listing.id(), null)).orElseThrow()
				.revision(1).orElseThrow();
		assertEquals(List.of("Điện", "Internet"),
				withTerms.termsOrNull().fees().stream().map(RevisionTerms.Fee::name).toList());
	}

	@Test
	void findRevision_fullForm_everyColumnAndFees() {
		Listing listing = stored();
		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		Instant captured = now().minusSeconds(60);
		Instant submitted = now();
		String submitter = ids.generateId();
		Map<String, Object> columns = merged(contentColumns(TITLE, DESCRIPTION), pendingTermsColumns(captured));
		columns.putAll(unitColumns());
		columns.put("submitted_at", Timestamp.from(submitted));
		columns.put("submitted_by", submitter);
		String revision = insertRow(listing.id(), 2, "PENDING_REVIEW", columns);
		insertFee(revision, 2, "Rác", "AMOUNT", 20_000L, "PER_MONTH");
		insertFee(revision, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");

		ListingRevision read = tx(() -> listings.findRevision(listing.id(), 2)).orElseThrow();

		assertAll(
				() -> assertEquals(revision, read.id()),
				() -> assertEquals(2, read.revisionNo()),
				() -> assertEquals(RevisionStatus.PENDING_REVIEW, read.status()),
				() -> assertEquals(new RevisionContent(TITLE, DESCRIPTION, MIXED), read.content()),
				() -> assertEquals(new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"), captured,
						9_500_000L, new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 5_000_000L), 12, 3,
						List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
								new RevisionTerms.Fee("Rác", FeeBasis.AMOUNT, 20_000L, ChargeUnit.PER_MONTH))),
						read.termsOrNull()),
				() -> assertEquals(new UnitSnapshot("APARTMENT", "48", "Đà Nẵng", "20194",
						"Phường Hải Châu", 2, 1, 32.5), read.unitSnapshotOrNull()),
				() -> assertEquals(submitted, read.submittedAtOrNull()),
				() -> assertEquals(submitter, read.submittedByOrNull()));
	}

	@Test
	void findRevision_unknownNumber_empty() {
		Listing listing = stored();
		Listing other = stored();

		assertTrue(tx(() -> listings.findRevision(listing.id(), 2)).isEmpty());
		assertTrue(tx(() -> listings.findRevision(ids.generateId(), 1)).isEmpty());
		assertEquals(other.revisions().getFirst().id(),
				tx(() -> listings.findRevision(other.id(), 1)).orElseThrow().id());
	}

	@Test
	void findRevision_repeatableReadAcrossACapture_oneSnapshot() {
		Listing listing = stored();
		String revision = listing.revisions().getFirst().id();
		jdbc.update("UPDATE listing_revisions SET terms_source = 'IN_EFFECT', terms_captured_at = ?,"
				+ " terms_monthly_rent = 8000000, terms_deposit_basis = 'MONTHS', terms_deposit_months = 2,"
				+ " terms_minimum_term_months = 6, terms_payment_period_months = 1 WHERE id = ?",
				Timestamp.from(now().minusSeconds(60)), revision);
		insertFee(revision, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");
		insertFee(revision, 2, "Internet", "INCLUDED", null, null);
		insertFee(revision, 3, "Nước", "PROVIDER_TARIFF", null, null);
		TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
		repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		repeatableRead.setReadOnly(true);

		ListingRevision inSnapshot = repeatableRead.execute(status -> {
			listings.findRow(listing.id()).orElseThrow();
			// a second capture commits on another connection between the listing row and the revision
			CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager).executeWithoutResult(inner -> {
				jdbc.update("UPDATE listing_revisions SET terms_monthly_rent = 9000000, terms_captured_at = ? WHERE id = ?",
						Timestamp.from(now()), revision);
				jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id = ?", revision);
				insertFee(revision, 1, "Gas", "AMOUNT", 1_000L, "ONE_OFF");
				insertFee(revision, 2, "Internet", "INCLUDED", null, null);
			})).join();
			return listings.findRevision(listing.id(), 1).orElseThrow();
		});

		assertEquals(8_000_000L, inSnapshot.termsOrNull().monthlyRent());
		assertEquals(List.of("Điện", "Internet", "Nước"),
				inSnapshot.termsOrNull().fees().stream().map(RevisionTerms.Fee::name).toList());
		ListingRevision after = tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow();
		assertEquals(9_000_000L, after.termsOrNull().monthlyRent());
		assertEquals(List.of("Gas", "Internet"), after.termsOrNull().fees().stream().map(RevisionTerms.Fee::name).toList());
	}

	@Test
	void findOpenRevisionNo_draftOrPending_numberElseEmpty() {
		Listing listing = stored();
		assertEquals(Optional.of(1), tx(() -> listings.findOpenRevisionNo(listing.id())));

		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		assertEquals(Optional.empty(), tx(() -> listings.findOpenRevisionNo(listing.id())));

		Map<String, Object> pending = merged(contentColumns(TITLE, DESCRIPTION), termsColumns());
		pending.putAll(unitColumns());
		pending.put("submitted_at", Timestamp.from(now()));
		pending.put("submitted_by", ids.generateId());
		insertRow(listing.id(), 2, "PENDING_REVIEW", pending);
		assertEquals(Optional.of(2), tx(() -> listings.findOpenRevisionNo(listing.id())));
	}

	@Test
	void findNewestRevisions_headerForm_contentIllegalState() {
		Listing listing = stored();

		ListingRevision header = tx(() -> listings.findNewestRevisions(listing.id(), 20)).revisions().getFirst();

		assertThrows(IllegalStateException.class, header::content);
		assertThrows(IllegalStateException.class, header::termsOrNull);
	}

	@Test
	void update_contentSaved_everyColumnRoundTrip() {
		Listing listing = stored();
		String actor = ids.generateId();

		Instant first = command(listing.id(), 1, loaded -> loaded.saveContent(1,
				new RevisionContent(TITLE, DESCRIPTION, EVERY), ids.generateId(), actor, now()));

		ListingRevision saved = tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow();
		assertEquals(new RevisionContent(TITLE, DESCRIPTION, EVERY), saved.content());
		assertEquals(first, saved.updatedAt());
		assertEquals(listing.revisions().getFirst().createdAt(), saved.createdAt());
		assertEquals(1L, rowVersion(listing.id()));
		assertEquals(List.of("CREATED", "REVISION_SAVED"), actions(listing.id()));

		command(listing.id(), 1, loaded -> loaded.saveContent(1, new RevisionContent("Tiêu đề", null,
				RevisionFeatures.NONE_CHOSEN), ids.generateId(), actor, now()));

		assertEquals(new RevisionContent("Tiêu đề", null, RevisionFeatures.NONE_CHOSEN),
				tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow().content());
		assertEquals(2L, rowVersion(listing.id()));
	}

	@Test
	void update_termsReplaced_feesReplaced() {
		Listing listing = stored();
		RevisionTerms three = terms(List.of(fee("Điện"), fee("Nước"), fee("Internet")));
		RevisionTerms two = terms(List.of(fee("Gas"), fee("Rác")));

		command(listing.id(), 1, loaded -> loaded.captureTerms(1, three, ids.generateId(), ids.generateId(), now()));
		assertEquals(three, tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow().termsOrNull());
		command(listing.id(), 1, loaded -> loaded.captureTerms(1, two, ids.generateId(), ids.generateId(), now()));

		assertEquals(two, tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow().termsOrNull());
		assertEquals(List.of("1 Gas", "2 Rác"), jdbc.queryForList("SELECT fee_no || ' ' || name FROM"
				+ " listing_revision_fees WHERE revision_id = ? ORDER BY fee_no", String.class,
				listing.revisions().getFirst().id()));
	}

	@Test
	void update_contentSaveAfterTerms_feesKept() {
		Listing listing = stored();
		RevisionTerms three = terms(List.of(fee("Điện"), fee("Nước"), fee("Internet")));
		command(listing.id(), 1, loaded -> loaded.captureTerms(1, three, ids.generateId(), ids.generateId(), now()));

		command(listing.id(), 1, loaded -> loaded.saveContent(1, new RevisionContent(TITLE, null, MIXED),
				ids.generateId(), ids.generateId(), now()));

		ListingRevision read = tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow();
		assertEquals(three, read.termsOrNull());
		assertEquals(TITLE, read.content().titleOrNull());
		assertEquals(3, count("SELECT count(*) FROM listing_revision_fees WHERE revision_id = ?",
				listing.revisions().getFirst().id()));
	}

	@Test
	void update_addedRevision_insertedWithHistory() {
		Listing listing = stored();
		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		String actor = ids.generateId();
		String revisionId = ids.generateId();

		command(listing.id(), null, loaded -> loaded.startRevision(revisionId, ids.generateId(), actor, now()));

		Map<String, Object> row = jdbc.queryForMap("SELECT id, status, created_by, title, feature_balcony FROM"
				+ " listing_revisions WHERE listing_id = ? AND revision_no = 2", listing.id());
		assertEquals(revisionId, row.get("id"));
		assertEquals("DRAFT", row.get("status"));
		assertEquals(actor, row.get("created_by"));
		assertNull(row.get("title"));
		assertEquals(false, row.get("feature_balcony"));
		assertEquals("REVISION_STARTED 2 " + actor, jdbc.queryForObject("SELECT action || ' ' || revision_no || ' '"
				+ " || actor_user_id FROM listing_history WHERE listing_id = ? AND action <> 'CREATED'", String.class,
				listing.id()));
		assertEquals(1L, rowVersion(listing.id()));
		assertEquals(Optional.of(2), tx(() -> listings.findOpenRevisionNo(listing.id())));
	}

	@Test
	void update_staleVersion_listingChangedNothingWritten() {
		Listing listing = stored();
		Listing loaded = tx(() -> listings.findForCommand(listing.id(), 1)).orElseThrow();
		jdbc.update("UPDATE listings SET row_version = row_version + 1 WHERE id = ?", listing.id());
		loaded.saveContent(1, new RevisionContent(TITLE, null, MIXED), ids.generateId(), ids.generateId(), now());

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.update(loaded);
			return null;
		}));

		assertNull(jdbc.queryForObject("SELECT title FROM listing_revisions WHERE listing_id = ?", String.class,
				listing.id()));
		assertEquals(List.of("CREATED"), actions(listing.id()));
		assertEquals(1L, rowVersion(listing.id()));
	}

	@Test
	void update_openRevisionWrittenBySql_listingChangedBackstop() {
		Listing listing = stored();
		jdbc.update("UPDATE listing_revisions SET status = 'CANCELLED' WHERE listing_id = ?", listing.id());
		Listing loaded = tx(() -> listings.findForCommand(listing.id(), null)).orElseThrow();
		// an open revision written without moving the listing's version: only the unique index can see it
		insertRevision(listing.id(), 3, "DRAFT");
		loaded.startRevision(ids.generateId(), ids.generateId(), ids.generateId(), now());

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.update(loaded);
			return null;
		}));

		assertEquals(0, count("SELECT count(*) FROM listing_revisions WHERE listing_id = ? AND revision_no = 2",
				listing.id()));
		assertEquals(0L, rowVersion(listing.id()));
		assertEquals(List.of("CREATED"), actions(listing.id()));
	}

	@Test
	void update_afterCall_noEntityManaged() {
		Listing listing = stored();
		RevisionTerms three = terms(List.of(fee("Điện"), fee("Nước"), fee("Internet")));

		List<String> managed = tx(() -> {
			Listing loaded = listings.findForCommand(listing.id(), 1).orElseThrow();
			loaded.saveContent(1, new RevisionContent(TITLE, DESCRIPTION, MIXED), ids.generateId(), ids.generateId(),
					now());
			loaded.captureTerms(1, three, ids.generateId(), ids.generateId(), now());
			listings.update(loaded);
			// each entity key names its entity class and id: none of listing's may remain in the persistence context
			return entityManager.unwrap(Session.class).getStatistics().getEntityKeys().stream()
					.map(String::valueOf)
					.filter(key -> key.contains(".listing."))
					.collect(Collectors.toList());
		});

		assertEquals(List.of(), managed);
		assertEquals(three, tx(() -> listings.findRevision(listing.id(), 1)).orElseThrow().termsOrNull());
	}

	/** One command in one transaction: load the working set, run it, write it; answers the time it used. */
	private Instant command(String listingId, Integer target, java.util.function.Consumer<Listing> work) {
		return tx(() -> {
			Listing loaded = listings.findForCommand(listingId, target).orElseThrow();
			work.accept(loaded);
			listings.update(loaded);
			return loaded.updatedAt();
		});
	}

	/** A listing created and inserted as the create does: revision 1 DRAFT and its CREATED row. */
	private Listing stored() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);
		return listing;
	}

	private static RevisionTerms terms(List<RevisionTerms.Fee> fees) {
		return new RevisionTerms(TermsSource.IN_EFFECT, null, now(), 8_000_000L,
				new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees);
	}

	private static RevisionTerms.Fee fee(String name) {
		return new RevisionTerms.Fee(name, FeeBasis.AMOUNT, 1_000L + name.length(), ChargeUnit.PER_MONTH);
	}

	private static Map<String, Object> contentColumns(String title, String description) {
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put("title", title);
		columns.put("description", description);
		columns.put("feature_furnishing", "FULL");
		columns.put("feature_air_conditioner", true);
		columns.put("feature_water_heater", false);
		columns.put("feature_fridge", true);
		columns.put("feature_washing_machine", "PRIVATE");
		columns.put("feature_kitchen", "SHARED");
		columns.put("feature_balcony", false);
		columns.put("feature_elevator", true);
		columns.put("feature_motorbike_parking", false);
		columns.put("feature_car_parking", true);
		columns.put("feature_internet", false);
		columns.put("feature_pets", "NOT_ALLOWED");
		columns.put("feature_no_shared_landlord", true);
		return columns;
	}

	private static Map<String, Object> termsColumns() {
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put("terms_source", "IN_EFFECT");
		columns.put("terms_captured_at", Timestamp.from(now()));
		columns.put("terms_monthly_rent", 8_000_000L);
		columns.put("terms_deposit_basis", "MONTHS");
		columns.put("terms_deposit_months", 2);
		columns.put("terms_minimum_term_months", 6);
		columns.put("terms_payment_period_months", 1);
		return columns;
	}

	private static Map<String, Object> pendingTermsColumns(Instant captured) {
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put("terms_source", "PENDING_START");
		columns.put("terms_pending_start_date", Date.valueOf(LocalDate.parse("2026-12-01")));
		columns.put("terms_captured_at", Timestamp.from(captured));
		columns.put("terms_monthly_rent", 9_500_000L);
		columns.put("terms_deposit_basis", "AMOUNT");
		columns.put("terms_deposit_amount", 5_000_000L);
		columns.put("terms_minimum_term_months", 12);
		columns.put("terms_payment_period_months", 3);
		return columns;
	}

	private static Map<String, Object> unitColumns() {
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put("unit_asset_type", "APARTMENT");
		columns.put("unit_province_code", "48");
		columns.put("unit_province_name", "Đà Nẵng");
		columns.put("unit_ward_code", "20194");
		columns.put("unit_ward_name", "Phường Hải Châu");
		columns.put("unit_bedrooms", 2);
		columns.put("unit_bathrooms", 1);
		columns.put("unit_area_sqm", 32.5);
		return columns;
	}

	private static Map<String, Object> merged(Map<String, Object> first, Map<String, Object> second) {
		Map<String, Object> columns = new LinkedHashMap<>(first);
		columns.putAll(second);
		return columns;
	}

	/** A revision row with the header columns and the given content columns (fixed names of this test only). */
	private String insertRow(String listingId, int number, String status, Map<String, Object> columns) {
		String id = ids.generateId();
		Timestamp now = Timestamp.from(now());
		List<Object> values = new ArrayList<>(List.of(id, listingId, number, status, ids.generateId(), now, now));
		values.addAll(columns.values());
		String names = columns.keySet().stream().map(name -> ", " + name).collect(Collectors.joining());
		String marks = columns.keySet().stream().map(name -> ", ?").collect(Collectors.joining());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at" + names + ") VALUES (?, ?, ?, ?, ?, ?, ?" + marks + ")", values.toArray());
		return id;
	}

	private void insertFee(String revisionId, int number, String name, String basis, Long amount, String chargeUnit) {
		jdbc.update("INSERT INTO listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", revisionId, number, name, basis, amount, chargeUnit);
	}

	private long rowVersion(String listingId) {
		return jdbc.queryForObject("SELECT row_version FROM listings WHERE id = ?", Long.class, listingId);
	}

	private List<String> actions(String listingId) {
		return jdbc.queryForList("SELECT action FROM listing_history WHERE listing_id = ? ORDER BY occurred_at, action",
				String.class, listingId);
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	private Listing listing(String agreementId, Instant now) {
		Listing listing = Listing.create(ids.generateId(), code(), ids.generateId(), agreementId, ids.generateId(), now,
				ids.generateId(), ids.generateId());
		created.add(listing.id());
		return listing;
	}

	private void insert(Listing listing) {
		tx(() -> {
			listings.insert(listing);
			return null;
		});
	}

	private void insertRevision(String listingId, int number, String status) {
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", ids.generateId(), listingId, number, status,
				ids.generateId(), now, now);
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static ListingCode code() {
		return ListingCode.draw(RANDOM);
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
