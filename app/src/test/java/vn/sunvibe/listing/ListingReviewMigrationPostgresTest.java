package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The four migrations of listing-publish E06-S4 on a real PostgreSQL (spec 5.3, E06S4-D8, E06S4-D18; RV-16): the decision
 * table with its primary key, cascade and the CHECKs that hold each outcome's shape; the nullable copy source; the queue
 * index; the access log with an id the application draws. The annotations are those of
 * {@link ListingFreshnessPersistencePostgresTest}, so this class shares its context; its rows are written with SQL under
 * listings it created, and removed children first. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingReviewMigrationPostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();

	/** The CHECKs of listing_revisions before this slice: it adds none (E06S4-D18). */
	private static final Set<String> REVISION_CHECKS = Set.of("ck_listing_revisions_number",
			"ck_listing_revisions_status", "ck_listing_revisions_feature_furnishing",
			"ck_listing_revisions_feature_washing_machine", "ck_listing_revisions_feature_kitchen",
			"ck_listing_revisions_feature_pets", "ck_listing_revisions_terms_source", "ck_listing_revisions_terms_shape",
			"ck_listing_revisions_terms_deposit", "ck_listing_revisions_unit_place",
			"ck_listing_revisions_unit_asset_type", "ck_listing_revisions_submission",
			"ck_listing_revisions_pending_content");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	private final List<String> created = new ArrayList<>();
	private final List<Long> logs = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		for (String id : created) {
			jdbc.update("DELETE FROM listing_revision_reviews WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
		for (Long id : logs) {
			jdbc.update("DELETE FROM listing_access_logs WHERE id = ?", id);
		}
		logs.clear();
	}

	@Test
	void migrations_applied_tablesColumnAndIndexValidReady() {
		assertEquals(List.of("revision_id", "listing_id", "outcome", "checklist_version", "change_reasons",
				"rejection_group", "note", "decided_by", "decided_at"), columns("listing_revision_reviews"));
		assertEquals(List.of("id", "user_id", "listing_id", "revision_no", "action", "accessed_at"),
				columns("listing_access_logs"));
		assertEquals(1, count("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
				+ " AND table_name = 'listing_revisions' AND column_name = 'copied_from_revision_no'"
				+ " AND data_type = 'integer'"));
		Map<String, Object> index = jdbc.queryForMap("SELECT i.indisvalid, i.indisready,"
				+ " pg_get_indexdef(i.indexrelid) AS definition FROM pg_index i"
				+ " WHERE i.indexrelid = 'idx_listing_revisions_review_queue'::regclass");
		assertEquals(true, index.get("indisvalid"));
		assertEquals(true, index.get("indisready"));
		String definition = String.valueOf(index.get("definition"));
		assertTrue(definition.contains("(submitted_at, id)"), definition);
		assertTrue(definition.contains("PENDING_REVIEW"), definition);
		List<Map<String, Object>> constraints = jdbc.queryForList("SELECT conname, convalidated FROM pg_constraint"
				+ " WHERE conrelid IN ('listing_revision_reviews'::regclass, 'listing_access_logs'::regclass)");
		assertEquals(Set.of("listing_revision_reviews_pkey", "fk_listing_revision_reviews_revision",
						"ck_listing_revision_reviews_outcome", "ck_listing_revision_reviews_checklist",
						"ck_listing_revision_reviews_reasons", "ck_listing_revision_reviews_group",
						"ck_listing_revision_reviews_note", "listing_access_logs_pkey", "ck_listing_access_logs_action"),
				constraints.stream().map(row -> (String) row.get("conname")).collect(Collectors.toSet()));
		for (Map<String, Object> row : constraints) {
			assertEquals(true, row.get("convalidated"), String.valueOf(row.get("conname")));
		}
		assertEquals("c", jdbc.queryForObject("SELECT confdeltype::text FROM pg_constraint"
				+ " WHERE conname = 'fk_listing_revision_reviews_revision'", String.class));
	}

	@Test
	void reviews_brokenShapeRows_refusedByChecks() {
		String listing = insertListingRow();

		assertAll(
				refused("ck_listing_revision_reviews_outcome",
						() -> insertReview(revision(listing), "PUBLISHED", null, null, null, "Ghi chú")),
				refused("ck_listing_revision_reviews_checklist",
						() -> insertReview(revision(listing), "APPROVED", null, null, null, null)),
				refused("ck_listing_revision_reviews_reasons",
						() -> insertReview(revision(listing), "APPROVED", 1, "OTHER", null, null)),
				refused("ck_listing_revision_reviews_group",
						() -> insertReview(revision(listing), "APPROVED", 1, null, "OTHER", null)),
				refused("ck_listing_revision_reviews_note",
						() -> insertReview(revision(listing), "APPROVED", 1, null, null, "Ghi chú")),
				refused("ck_listing_revision_reviews_reasons",
						() -> insertReview(revision(listing), "NEEDS_CHANGES", null, null, null, "Ghi chú")),
				refused("ck_listing_revision_reviews_reasons",
						() -> insertReview(revision(listing), "NEEDS_CHANGES", null, "PHOTO_NOT_ACCEPTED", null,
								"Ghi chú")),
				refused("ck_listing_revision_reviews_reasons",
						() -> insertReview(revision(listing), "NEEDS_CHANGES", null, "OTHER,", null, "Ghi chú")),
				refused("ck_listing_revision_reviews_group",
						() -> insertReview(revision(listing), "REJECTED", null, null, null, "Ghi chú")),
				refused("ck_listing_revision_reviews_group",
						() -> insertReview(revision(listing), "REJECTED", null, null, "SPAM", "Ghi chú")),
				refused("ck_listing_revision_reviews_checklist",
						() -> insertReview(revision(listing), "APPROVED", 0, null, null, null)));
		insertReview(revision(listing), "APPROVED", 1, null, null, null);
		insertReview(revision(listing), "NEEDS_CHANGES", null, "CONTENT_MISMATCH,TERMS_MISMATCH,OTHER", null, "Ghi chú");
		insertReview(revision(listing), "REJECTED", null, null, "WRONG_UNIT", "Ghi chú");
		assertEquals(3, count("SELECT count(*) FROM listing_revision_reviews WHERE listing_id = ?", listing));
	}

	@Test
	void reviews_secondDecision_primaryKeyRefused() {
		String listing = insertListingRow();
		String revision = revision(listing);
		insertReview(revision, "REJECTED", null, null, "OTHER", "Ghi chú");

		DataIntegrityViolationException refusal = assertThrows(DataIntegrityViolationException.class,
				() -> insertReview(revision, "APPROVED", 1, null, null, null));

		assertTrue(String.valueOf(refusal.getMostSpecificCause().getMessage()).contains("listing_revision_reviews_pkey"));
		assertEquals("REJECTED", jdbc.queryForObject("SELECT outcome FROM listing_revision_reviews WHERE revision_id = ?",
				String.class, revision));
	}

	@Test
	void reviews_revisionDeleted_decisionGone() {
		String listing = insertListingRow();
		String revision = revision(listing);
		insertReview(revision, "NEEDS_CHANGES", null, "OTHER", null, "Ghi chú");

		jdbc.update("DELETE FROM listing_revisions WHERE id = ?", revision);

		assertEquals(0, count("SELECT count(*) FROM listing_revision_reviews WHERE revision_id = ?", revision));
	}

	@Test
	void accessLogs_idHasNoDefaultNoIdentity() {
		Map<String, Object> id = jdbc.queryForMap("SELECT column_default, is_identity, data_type"
				+ " FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'listing_access_logs'"
				+ " AND column_name = 'id'");

		assertNull(id.get("column_default"));
		assertEquals("NO", id.get("is_identity"));
		assertEquals("bigint", id.get("data_type"));
		assertEquals(0, count("SELECT count(*) FROM pg_constraint WHERE contype = 'f'"
				+ " AND conrelid = 'listing_access_logs'::regclass"));
	}

	@Test
	void accessLogs_otherAction_refused() {
		long other = nextLogId();
		long opened = nextLogId();

		assertAll(refused("ck_listing_access_logs_action", () -> insertLog(other, "UNIT_LOCATION_READ")));
		insertLog(opened, "UNIT_LOCATION_OPENED");
		assertEquals(1, count("SELECT count(*) FROM listing_access_logs WHERE id = ?", opened));
	}

	@Test
	void copySource_nullableNoCheckOnRevisions() {
		Map<String, Object> column = jdbc.queryForMap("SELECT is_nullable, column_default FROM information_schema.columns"
				+ " WHERE table_schema = 'public' AND table_name = 'listing_revisions'"
				+ " AND column_name = 'copied_from_revision_no'");
		List<String> checks = jdbc.queryForList("SELECT conname FROM pg_constraint WHERE contype = 'c'"
				+ " AND conrelid = 'listing_revisions'::regclass", String.class);

		assertEquals("YES", column.get("is_nullable"));
		assertNull(column.get("column_default"));
		assertEquals(REVISION_CHECKS, Set.copyOf(checks));
		assertEquals(REVISION_CHECKS.size(), checks.size());
		String listing = insertListingRow();
		String revision = revision(listing);
		jdbc.update("UPDATE listing_revisions SET copied_from_revision_no = 1 WHERE id = ?", revision);
		assertEquals(1, count("SELECT copied_from_revision_no FROM listing_revisions WHERE id = ?", revision));
	}

	private List<String> columns(String table) {
		return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public'"
				+ " AND table_name = ? ORDER BY ordinal_position", String.class, table);
	}

	/** A revision row of the listing, numbered after its greatest one, in the status CANCELLED (a closed revision). */
	private String revision(String listingId) {
		String id = ids.generateId();
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at) SELECT ?, ?, coalesce(max(revision_no), 0) + 1, 'CANCELLED', ?, ?, ?"
				+ " FROM listing_revisions WHERE listing_id = ?", id, listingId, ids.generateId(), now, now, listingId);
		return id;
	}

	private void insertReview(String revisionId, String outcome, Integer checklistVersion, String changeReasons,
							  String rejectionGroup, String note) {
		jdbc.update("INSERT INTO listing_revision_reviews (revision_id, listing_id, outcome, checklist_version,"
						+ " change_reasons, rejection_group, note, decided_by, decided_at)"
						+ " SELECT id, listing_id, ?, ?, ?, ?, ?, ?, ? FROM listing_revisions WHERE id = ?", outcome,
				checklistVersion, changeReasons, rejectionGroup, note, ids.generateId(), Timestamp.from(now()),
				revisionId);
	}

	private void insertLog(long id, String action) {
		logs.add(id);
		jdbc.update("INSERT INTO listing_access_logs (id, user_id, listing_id, revision_no, action, accessed_at)"
				+ " VALUES (?, ?, ?, 1, ?, ?)", id, ids.generateId(), ids.generateId(), action, Timestamp.from(now()));
	}

	/** An id no other row of the log holds. */
	private long nextLogId() {
		return Math.abs(RANDOM.nextLong() % 1_000_000_000_000L) + 1;
	}

	/** A listings row written with SQL only, on its own agreement; otherwise valid. */
	private String insertListingRow() {
		String id = ids.generateId();
		created.add(id);
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, 'UNPUBLISHED', ?, ?, ?)",
				id, ListingCode.draw(RANDOM).value(), ids.generateId(), ids.generateId(), ids.generateId(), now, now);
		return id;
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	/** One case: the insert is refused by the named constraint. */
	private static Executable refused(String constraint, Runnable insert) {
		return () -> {
			DataIntegrityViolationException refusal =
					assertThrows(DataIntegrityViolationException.class, insert::run, constraint);
			assertTrue(String.valueOf(refusal.getMostSpecificCause().getMessage()).contains(constraint), constraint);
		};
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
