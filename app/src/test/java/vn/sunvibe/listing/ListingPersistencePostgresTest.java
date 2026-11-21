package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRevisionPage;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ListingRowPage;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The listing tables, their constraints and the listing repository on a real PostgreSQL (listing-publish E06-S1 spec 5.3,
 * E06S1-D4, E06S1-D7, E06S1-D17; LC-13, LC-18; listing BR-1, sections 3.2, 3.3). The annotations are those of
 * {@link vn.sunvibe.FlywayPostgresMigrationTest}, so both share one context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingPersistencePostgresTest {

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
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
	}

	@Test
	void insert_newListing_threeRowsReadBack() {
		Instant now = Instant.parse("2026-11-20T03:15:00.123456789Z").truncatedTo(ChronoUnit.MICROS);
		String agreementId = ids.generateId();
		Listing listing = listing(agreementId, now);

		insert(listing);

		assertEquals(new ListingRow(listing.id(), listing.code(), listing.unitId(), TransactionType.LONG_TERM_RENT,
						agreementId, ListingState.UNPUBLISHED, listing.createdBy(), now, 0L),
				tx(() -> listings.findRow(listing.id())).orElseThrow());
		Map<String, Object> row = jdbc.queryForMap("SELECT transaction_type, state, row_version, updated_at"
				+ " FROM listings WHERE id = ?", listing.id());
		assertEquals("LONG_TERM_RENT", row.get("transaction_type"));
		assertEquals("UNPUBLISHED", row.get("state"));
		assertEquals(0L, ((Number) row.get("row_version")).longValue());
		assertEquals(now, ((Timestamp) row.get("updated_at")).toInstant());
		ListingRevision revision = tx(() -> listings.findNewestRevisions(listing.id(), 20)).revisions().getFirst();
		ListingRevision built = listing.revisions().getFirst();
		assertEquals(built.id(), revision.id());
		assertEquals(1, revision.revisionNo());
		assertEquals(RevisionStatus.DRAFT, revision.status());
		assertEquals(listing.createdBy(), revision.createdBy());
		assertEquals(now, revision.createdAt());
		assertEquals(now, revision.updatedAt());
		Map<String, Object> history = jdbc.queryForMap("SELECT id, action, revision_no, actor_user_id, occurred_at"
				+ " FROM listing_history WHERE listing_id = ?", listing.id());
		assertEquals(listing.pendingHistory().getFirst().id(), history.get("id"));
		assertEquals("CREATED", history.get("action"));
		assertEquals(1, ((Number) history.get("revision_no")).intValue());
		assertEquals(listing.createdBy(), history.get("actor_user_id"));
		assertEquals(now, ((Timestamp) history.get("occurred_at")).toInstant());
	}

	@Test
	void insert_secondOpenListingOfTheAgreement_openListingExists() {
		String agreementId = ids.generateId();
		insert(listing(agreementId, now()));

		Listing second = listing(agreementId, now());
		assertThrows(OpenListingExistsException.class, () -> insert(second));
	}

	@Test
	void insert_takenCode_codeTaken() {
		Listing first = listing(ids.generateId(), now());
		insert(first);

		Listing second = Listing.create(ids.generateId(), first.code(), ids.generateId(), ids.generateId(),
				ids.generateId(), now(), ids.generateId(), ids.generateId());
		created.add(second.id());
		assertThrows(ListingCodeTakenException.class, () -> insert(second));
	}

	@Test
	void insert_afterAnArchivedListing_stored() {
		String agreementId = ids.generateId();
		Listing first = listing(agreementId, now());
		insert(first);
		jdbc.update("UPDATE listings SET state = 'ARCHIVED' WHERE id = ?", first.id());

		Listing second = listing(agreementId, now());
		insert(second);

		assertTrue(tx(() -> listings.findRow(second.id())).isPresent());
	}

	@Test
	void checks_listingRow_offItsChecksRefused() {
		assertAll(
				refused("ck_listings_code",
						() -> insertListingRow("TD-I345-6789", "LONG_TERM_RENT", "UNPUBLISHED")),
				refused("ck_listings_state",
						() -> insertListingRow(code().value(), "LONG_TERM_RENT", "LOST")),
				refused("ck_listings_transaction_type",
						() -> insertListingRow(code().value(), "SALE", "UNPUBLISHED")));
	}

	@Test
	void checks_revisionRows_offTheirRulesRefused() {
		String open = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");
		insertRevision(open, 1, "DRAFT");
		String approved = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");
		insertRevision(approved, 1, "REPLACED");
		insertRevision(approved, 2, "APPROVED");
		String live = insertListingRow(code().value(), "LONG_TERM_RENT", "PUBLISHED");
		insertRevision(live, 1, "REPLACED");
		insertRevision(live, 2, "LIVE");

		assertAll(
				refused("uq_listing_revisions_open", () -> insertRevision(open, 2, "DRAFT")),
				refused("uq_listing_revisions_open", () -> insertRevision(open, 2, "PENDING_REVIEW")),
				refused("uq_listing_revisions_number", () -> insertRevision(open, 1, "REPLACED")),
				refused("ck_listing_revisions_number", () -> insertRevision(open, 0, "REPLACED")),
				refused("ck_listing_revisions_status", () -> insertRevision(open, 3, "DONE")),
				refused("uq_listing_revisions_approved", () -> insertRevision(approved, 3, "APPROVED")),
				refused("uq_listing_revisions_live", () -> insertRevision(live, 3, "LIVE")));
	}

	@Test
	void findNewestRevisions_twentyOne_twentyNewestAndOlder() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);
		IntStream.rangeClosed(2, 21).forEach(number -> insertRevision(listing.id(), number, "REPLACED"));

		ListingRevisionPage page = tx(() -> listings.findNewestRevisions(listing.id(), 20));

		assertEquals(IntStream.iterate(21, number -> number - 1).limit(20).boxed().toList(),
				page.revisions().stream().map(ListingRevision::revisionNo).toList());
		assertTrue(page.hasOlder());
	}

	@Test
	void findNewestRevisions_twenty_allNoOlder() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);
		IntStream.rangeClosed(2, 20).forEach(number -> insertRevision(listing.id(), number, "REPLACED"));

		ListingRevisionPage page = tx(() -> listings.findNewestRevisions(listing.id(), 20));

		assertEquals(IntStream.iterate(20, number -> number - 1).limit(20).boxed().toList(),
				page.revisions().stream().map(ListingRevision::revisionNo).toList());
		assertFalse(page.hasOlder());
	}

	@Test
	void findNewestByAgreement_twentyOne_twentyNewestAndOlder() {
		String agreementId = ids.generateId();
		Instant start = now().minusSeconds(60);
		List<String> inOrder = new ArrayList<>();
		for (int index = 0; index < 21; index++) {
			Listing listing = listing(agreementId, start.plusSeconds(index));
			insert(listing);
			inOrder.add(listing.id());
			if (index < 20) {
				jdbc.update("UPDATE listings SET state = 'ARCHIVED' WHERE id = ?", listing.id());
			}
		}

		ListingRowPage page = tx(() -> listings.findNewestByAgreement(agreementId, 20));

		assertEquals(inOrder.reversed().subList(0, 20), page.rows().stream().map(ListingRow::listingId).toList());
		assertEquals(ListingState.UNPUBLISHED, page.rows().getFirst().storedState());
		assertEquals(ListingState.ARCHIVED, page.rows().get(1).storedState());
		assertTrue(page.hasOlder());
	}

	@Test
	void hasOpen_archivedOnly_false() {
		String agreementId = ids.generateId();
		Listing listing = listing(agreementId, now());
		insert(listing);
		assertTrue(tx(() -> listings.hasOpen(agreementId)));

		jdbc.update("UPDATE listings SET state = 'ARCHIVED' WHERE id = ?", listing.id());

		assertFalse(tx(() -> listings.hasOpen(agreementId)));
		assertFalse(tx(() -> listings.hasOpen(ids.generateId())));
	}

	@Test
	void existsByCode_storedCode_true() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);

		assertTrue(tx(() -> listings.existsByCode(listing.code().value())));
		assertFalse(tx(() -> listings.existsByCode(code().value())));
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

	/** A listings row written with SQL only, on its own agreement; otherwise valid. */
	private String insertListingRow(String code, String transactionType, String state) {
		String id = ids.generateId();
		created.add(id);
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				id, code, ids.generateId(), transactionType, ids.generateId(), state, ids.generateId(), now, now);
		return id;
	}

	private void insertRevision(String listingId, int number, String status) {
		Timestamp now = Timestamp.from(now());
		if ("PENDING_REVIEW".equals(status)) {
			// listing-publish E06-S2 spec 5.3: a Pending review row holds its content, terms, unit snapshot and submission
			jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
					+ " updated_at, title, description, terms_source, terms_captured_at, terms_monthly_rent,"
					+ " terms_deposit_basis, terms_deposit_months, terms_minimum_term_months, terms_payment_period_months,"
					+ " unit_bedrooms, unit_bathrooms, unit_area_sqm, submitted_at, submitted_by) VALUES (?, ?, ?, ?, ?,"
					+ " ?, ?, 'Title', 'Description', 'IN_EFFECT', ?, 8000000, 'MONTHS', 2, 6, 1, 1, 1, 25, ?, ?)",
					ids.generateId(), listingId, number, status, ids.generateId(), now, now, now, now, ids.generateId());
			return;
		}
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", ids.generateId(), listingId, number, status,
				ids.generateId(), now, now);
	}

	/** One case: the insert is refused by the named constraint. */
	private static Executable refused(String constraint, Runnable insert) {
		return () -> {
			DataIntegrityViolationException refusal =
					assertThrows(DataIntegrityViolationException.class, insert::run, constraint);
			assertTrue(String.valueOf(refusal.getMostSpecificCause().getMessage()).contains(constraint), constraint);
		};
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
