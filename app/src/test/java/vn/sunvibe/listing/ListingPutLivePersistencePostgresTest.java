package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.PutLiveMoment;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The persistence of a put live on a real PostgreSQL (listing-publish E06-S6 spec E06S6-D6 "The one write", "The write
 * backstop", D7; PL-1, PL-4, PL-14 persistence halves): the one guarded write that now stores the state, the revision
 * rows in the aggregate's order (the replaced one first), the history with its moment, the row lock, the working set of
 * a decision with the live revision, the record that puts live with its own version, and the Live index answered as a
 * concurrent change. The annotations are those of {@link ListingReviewPersistencePostgresTest}, so this class shares its
 * context; its rows are seeded by SQL first (testing.md), children deleted first, times from the JVM clock. Needs
 * Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingPutLivePersistencePostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		for (String id : created) {
			jdbc.update("DELETE FROM listing_freshness_records WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id IN"
					+ " (SELECT id FROM listing_revisions WHERE listing_id = ?)", id);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
	}

	/** PL-1: an Unpublished listing's Approved revision put live: Published, Live with its time, one history row. */
	@Test
	void update_putLiveFromUnpublished_stateTimesHistoryOneVersionMove() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", null);
		long version = rowVersion(listingId);
		Instant now = now();
		String actor = ids.generateId();

		tx(() -> {
			Listing listing = listings.findForPutLive(listingId, 1).orElseThrow();
			listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now), PutLiveMoment.SALE_COMMAND, actor,
					now, ids.generateId(), ids.generateId());
			listings.update(listing);
			return null;
		});

		assertAll(
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals(version + 1, rowVersion(listingId)),
				() -> assertEquals("LIVE", status(listingId, 1)),
				() -> assertEquals(now, time(listingId, 1, "went_live_at")),
				() -> assertNull(time(listingId, 1, "replaced_at")),
				() -> assertEquals(List.of("REVISION_PUT_LIVE 1 " + actor), history(listingId)));
	}

	/** PL-1: a Published listing with a Live revision: the former Replaced with its time, the target Live. */
	@Test
	void update_putLiveReplacingLive_replacedThenLiveRows() {
		String listingId = insertListingRow("PUBLISHED");
		Instant firstLive = now().minusSeconds(3_600);
		insertFull(listingId, 1, "LIVE", firstLive);
		insertFull(listingId, 2, "APPROVED", null);
		long version = rowVersion(listingId);
		Instant now = now();
		String actor = ids.generateId();

		tx(() -> {
			Listing listing = listings.findForPutLive(listingId, 2).orElseThrow();
			listing.tryPutLive(2, holding(listing, 2, ListingState.PUBLISHED, now), PutLiveMoment.SALE_COMMAND, actor,
					now, ids.generateId(), ids.generateId());
			listings.update(listing);
			return null;
		});

		assertAll(
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals(version + 1, rowVersion(listingId)),
				() -> assertEquals(List.of("REPLACED", "LIVE"), List.of(status(listingId, 1), status(listingId, 2))),
				() -> assertEquals(firstLive, time(listingId, 1, "went_live_at")),
				() -> assertEquals(now, time(listingId, 1, "replaced_at")),
				() -> assertEquals(now, time(listingId, 2, "went_live_at")),
				() -> assertEquals(Set.of("REVISION_REPLACED 1 " + actor, "REVISION_PUT_LIVE 2 " + actor),
						Set.copyOf(history(listingId))));
	}

	/**
	 * PL-14 backstop (E06S6-D6): a Live row written by SQL without moving the version after the load; the update that
	 * puts the Approved revision live meets the Live index and answers a concurrent change; nothing is written.
	 */
	@Test
	void update_secondLiveRowWithoutVersionMove_listingChangedNothingWritten() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", null);
		long version = rowVersion(listingId);
		Instant now = now();
		Listing listing = tx(() -> listings.findForPutLive(listingId, 1).orElseThrow());
		listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now), PutLiveMoment.SALE_COMMAND,
				ids.generateId(), now, ids.generateId(), ids.generateId());
		insertFull(listingId, 2, "LIVE", now().minusSeconds(60));

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.update(listing);
			return null;
		}));

		assertAll(
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(version, rowVersion(listingId)),
				() -> assertEquals(List.of("APPROVED", "LIVE"), List.of(status(listingId, 1), status(listingId, 2))),
				() -> assertEquals(List.of(), history(listingId)));
	}

	/** listing BR-3: a version moved after the load answers a concurrent change; nothing is written. */
	@Test
	void update_movedVersion_listingChangedNothingWritten() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", null);
		Instant now = now();
		Listing listing = tx(() -> listings.findForPutLive(listingId, 1).orElseThrow());
		listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now), PutLiveMoment.SALE_COMMAND,
				ids.generateId(), now, ids.generateId(), ids.generateId());
		jdbc.update("UPDATE listings SET row_version = row_version + 1 WHERE id = ?", listingId);
		long moved = rowVersion(listingId);

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.update(listing);
			return null;
		}));

		assertAll(
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(moved, rowVersion(listingId)),
				() -> assertEquals("APPROVED", status(listingId, 1)),
				() -> assertEquals(List.of(), history(listingId)));
	}

	/** E06S6-D5 step (8): the row lock answers the locked row version; an unknown listing answers none. */
	@Test
	void lockRow_answersLockedVersion() {
		String listingId = insertListingRow("UNPUBLISHED");
		jdbc.update("UPDATE listings SET row_version = 5 WHERE id = ?", listingId);

		assertEquals(Optional.of(5L), tx(() -> listings.lockRow(listingId)));
		assertEquals(Optional.empty(), tx(() -> listings.lockRow(ids.generateId())));
	}

	/** E06S6-D7: a decision's working set holds the target, the approved revision waiting and the live revision. */
	@Test
	void findForReview_withLive_loadsTargetApprovedAndLive() {
		String listingId = insertListingRow("PUBLISHED");
		insertFull(listingId, 1, "LIVE", now());
		insertFull(listingId, 2, "APPROVED", null);
		insertFull(listingId, 3, "CANCELLED", null);
		insertFull(listingId, 4, "PENDING_REVIEW", null);

		Listing listing = tx(() -> listings.findForReview(listingId, 4).orElseThrow());

		assertEquals(Map.of(1, "LIVE", 2, "APPROVED", 4, "PENDING_REVIEW"), statuses(listing));
	}

	/**
	 * PL-4 (E06S6-D6, D7): a valid record that puts live: the record row on the version its own guarded write produces,
	 * the summary columns, the state, the revision rows and the history of the record and the put live.
	 */
	@Test
	void recordFreshnessWithPutLive_valid_stateRevisionsRecordHistory() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "LIVE", now().minusSeconds(3_600));
		insertFull(listingId, 2, "APPROVED", null);
		long version = rowVersion(listingId);
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		String sale = ids.generateId();

		tx(() -> {
			Listing listing = listings.findForPutLive(listingId, null).orElseThrow();
			listing.tryPutLive(2, holding(listing, 2, ListingState.UNPUBLISHED, now), PutLiveMoment.VALID_CONFIRMATION,
					null, now, ids.generateId(), ids.generateId());
			FreshnessRecord record = record(listingId, version + 1, today, now, sale);
			listings.recordFreshnessWithPutLive(listing, version, now, new FreshnessSummary(today, true), record,
					entry(sale, now));
			return null;
		});

		assertAll(
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals(version + 1, rowVersion(listingId)),
				() -> assertEquals(version + 1, jdbc.queryForObject("SELECT listing_row_version FROM"
						+ " listing_freshness_records WHERE listing_id = ?", Long.class, listingId)),
				() -> assertEquals(today + " true", jdbc.queryForObject("SELECT freshness_confirmed_on::text || ' '"
						+ " || freshness_latest_valid::text FROM listings WHERE id = ?", String.class, listingId)),
				() -> assertEquals(List.of("REPLACED", "LIVE"), List.of(status(listingId, 1), status(listingId, 2))),
				() -> assertEquals(now, time(listingId, 1, "replaced_at")),
				() -> assertEquals(now, time(listingId, 2, "went_live_at")),
				() -> assertEquals(Set.of("FRESHNESS_RECORDED - " + sale, "REVISION_REPLACED 1 -",
						"REVISION_PUT_LIVE_ON_CONFIRMATION 2 -"), Set.copyOf(history(listingId))));
	}

	/**
	 * listing BR-3 (E06S6-D6, D7): a version moved after the load, by a command that committed in between, answers a
	 * concurrent change from the guarded statement; nothing of the record, the summary or the put live is written.
	 */
	@Test
	void recordFreshnessWithPutLive_movedVersion_listingChangedNothingWritten() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", null);
		long version = rowVersion(listingId);
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		String sale = ids.generateId();
		Listing listing = tx(() -> listings.findForPutLive(listingId, null).orElseThrow());
		listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now), PutLiveMoment.VALID_CONFIRMATION, null,
				now, ids.generateId(), ids.generateId());
		jdbc.update("UPDATE listings SET row_version = row_version + 1 WHERE id = ?", listingId);
		long moved = rowVersion(listingId);

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.recordFreshnessWithPutLive(listing, version, now, new FreshnessSummary(today, true),
					record(listingId, version + 1, today, now, sale), entry(sale, now));
			return null;
		}));

		assertAll(
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(moved, rowVersion(listingId)),
				() -> assertEquals("APPROVED", status(listingId, 1)),
				() -> assertNull(time(listingId, 1, "went_live_at")),
				() -> assertEquals(0, count("SELECT count(*) FROM listing_freshness_records WHERE listing_id = ?",
						listingId)),
				() -> assertEquals(0, count("SELECT count(*) FROM listings WHERE id = ? AND (freshness_confirmed_on IS NOT"
						+ " NULL OR freshness_latest_valid IS NOT NULL)", listingId)),
				() -> assertEquals(List.of(), history(listingId)));
	}

	/** E06S6-D6: a caller that breaks either invariant of the record's version is a defect; nothing is written. */
	@Test
	void recordFreshnessWithPutLive_versionNotLoaded_illegalStateNothingWritten() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", null);
		long version = rowVersion(listingId);
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		String sale = ids.generateId();

		assertAll(
				() -> assertThrows(IllegalStateException.class, () -> tx(() -> {
					Listing listing = listings.findForPutLive(listingId, null).orElseThrow();
					listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now),
							PutLiveMoment.VALID_CONFIRMATION, null, now, ids.generateId(), ids.generateId());
					listings.recordFreshnessWithPutLive(listing, version + 1, now, new FreshnessSummary(today, true),
							record(listingId, version + 2, today, now, sale), entry(sale, now));
					return null;
				}), "loaded version differs"),
				() -> assertThrows(IllegalStateException.class, () -> tx(() -> {
					Listing listing = listings.findForPutLive(listingId, null).orElseThrow();
					listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now),
							PutLiveMoment.VALID_CONFIRMATION, null, now, ids.generateId(), ids.generateId());
					listings.recordFreshnessWithPutLive(listing, version, now, new FreshnessSummary(today, true),
							record(listingId, version + 2, today, now, sale), entry(sale, now));
					return null;
				}), "record version not the next"));

		assertAll(
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(version, rowVersion(listingId)),
				() -> assertEquals("APPROVED", status(listingId, 1)),
				() -> assertEquals(0, count("SELECT count(*) FROM listing_freshness_records WHERE listing_id = ?",
						listingId)),
				() -> assertEquals(List.of(), history(listingId)));
	}

	/** Every display condition holding for the loaded revision: its own terms in effect, available, confirmed today. */
	private static DisplayConditions holding(Listing listing, int revisionNo, ListingState state, Instant now) {
		return new DisplayConditions(state, false, true, List.of(),
				listing.revision(revisionNo).orElseThrow().termsOrNull(), UnitAvailability.AVAILABLE, false,
				new FreshnessSummary(FreshnessPolicy.today(now), true));
	}

	private FreshnessRecord record(String listingId, long listingRowVersion, LocalDate today, Instant now, String by) {
		return new FreshnessRecord(ids.generateId(), listingId, listingRowVersion, today, now, by, FreshnessChannel.CALL,
				FreshnessOutcome.AVAILABLE, true, true, null);
	}

	private ListingHistoryEntry entry(String by, Instant now) {
		return new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED, null, by, now);
	}

	/**
	 * A revision with every column a Pending review revision needs, in the status given; a Live one went live at the
	 * time given (E06S6-D6: the CHECKs ask for it).
	 */
	private void insertFull(String listingId, int number, String status, Instant wentLiveAtOrNull) {
		Timestamp at = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at, title, description, terms_source, terms_captured_at, terms_monthly_rent,"
				+ " terms_deposit_basis, terms_deposit_months, terms_minimum_term_months, terms_payment_period_months,"
				+ " unit_asset_type, unit_province_code, unit_province_name, unit_ward_code, unit_ward_name, unit_bedrooms,"
				+ " unit_bathrooms, unit_area_sqm, submitted_at, submitted_by, went_live_at) VALUES (?, ?, ?, ?, ?, ?, ?,"
				+ " 'Phòng trọ mới gần chợ Hải Châu', ?, 'IN_EFFECT', ?, 8000000, 'MONTHS', 2, 6, 1, 'APARTMENT', '48',"
				+ " 'Đà Nẵng', '20194', 'Phường Hải Châu', 2, 1, 32.5, ?, ?, ?)", ids.generateId(), listingId, number,
				status, ids.generateId(), at, at, "Mô tả ".repeat(30), at, at, ids.generateId(),
				wentLiveAtOrNull == null ? null : Timestamp.from(wentLiveAtOrNull));
	}

	/** A listings row written with SQL only, on its own agreement, in the stored state given. */
	private String insertListingRow(String state) {
		String id = ids.generateId();
		created.add(id);
		Timestamp at = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, ?, ?, ?, ?)",
				id, ListingCode.draw(RANDOM).value(), ids.generateId(), ids.generateId(), state, ids.generateId(), at, at);
		return id;
	}

	private static Map<Integer, String> statuses(Listing listing) {
		java.util.Map<Integer, String> answer = new java.util.TreeMap<>();
		for (ListingRevision revision : listing.revisions()) {
			answer.put(revision.revisionNo(), revision.status().name());
		}
		return answer;
	}

	private String state(String listingId) {
		return jdbc.queryForObject("SELECT state FROM listings WHERE id = ?", String.class, listingId);
	}

	private String status(String listingId, int revisionNo) {
		return jdbc.queryForObject("SELECT status FROM listing_revisions WHERE listing_id = ? AND revision_no = ?",
				String.class, listingId, revisionNo);
	}

	private Instant time(String listingId, int revisionNo, String column) {
		Timestamp value = jdbc.queryForObject("SELECT " + column + " FROM listing_revisions WHERE listing_id = ?"
				+ " AND revision_no = ?", Timestamp.class, listingId, revisionNo);
		return value == null ? null : value.toInstant();
	}

	/** The listing's history as "action revision actor", with "-" for none. */
	private List<String> history(String listingId) {
		return jdbc.queryForList("SELECT action || ' ' || coalesce(revision_no::text, '-') || ' '"
				+ " || coalesce(actor_user_id, '-') FROM listing_history WHERE listing_id = ? ORDER BY occurred_at, id",
				String.class, listingId);
	}

	private long rowVersion(String listingId) {
		return jdbc.queryForObject("SELECT row_version FROM listings WHERE id = ?", Long.class, listingId);
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
