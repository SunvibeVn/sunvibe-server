package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * The two revision times and their CHECKs on a real PostgreSQL (listing-publish E06-S6 spec E06S6-D6 "Times", PL-14
 * migration half): a Live or Replaced revision has the time it went live, only a Replaced one has the time it was
 * replaced, and no other status has either; both CHECKs validated after the migration (SV-333 (1)). The annotations,
 * autowired fields, {@code RANDOM}, {@code created} with its cleanup and the helpers {@code insertListingRow},
 * {@code refused}, {@code code} and {@code now} are those of {@link ListingRevisionMigrationPostgresTest}, so this class
 * shares its context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingRevisionLiveTimesMigrationPostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String WENT_LIVE = "ck_listing_revisions_went_live_at";
	private static final String REPLACED = "ck_listing_revisions_replaced_at";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

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
	void checks_liveWithoutWentLiveAt_refusedByName() {
		String listing = insertListingRow();

		assertAll(refused(WENT_LIVE, () -> insertRevision(listing, 1, "LIVE", null, null)));
	}

	@Test
	void checks_replacedWithoutReplacedAt_refusedByName() {
		String listing = insertListingRow();

		assertAll(
				refused(REPLACED, () -> insertRevision(listing, 1, "REPLACED", time(), null)),
				refused(WENT_LIVE, () -> insertRevision(listing, 1, "REPLACED", null, time())));
	}

	@Test
	void checks_otherStatusWithATime_refusedByName() {
		String listing = insertListingRow();

		assertAll(
				refused(WENT_LIVE, () -> insertRevision(listing, 1, "APPROVED", time(), null)),
				refused(REPLACED, () -> insertRevision(listing, 1, "APPROVED", null, time())),
				refused(REPLACED, () -> insertRevision(listing, 1, "LIVE", time(), time())),
				refused(WENT_LIVE, () -> insertRevision(listing, 1, "DRAFT", time(), null)));
	}

	@Test
	void checks_liveAndReplacedWithTheirTimes_stored() {
		String listing = insertListingRow();
		Timestamp wentLive = time();
		Timestamp replaced = time();

		assertDoesNotThrow(() -> insertRevision(listing, 1, "REPLACED", wentLive, replaced));
		assertDoesNotThrow(() -> insertRevision(listing, 2, "LIVE", wentLive, null));

		assertEquals(List.of("1 REPLACED true true", "2 LIVE true false"), jdbc.queryForList(
				"SELECT revision_no || ' ' || status || ' ' || (went_live_at IS NOT NULL) || ' '"
						+ " || (replaced_at IS NOT NULL) FROM listing_revisions WHERE listing_id = ? ORDER BY revision_no",
				String.class, listing));
	}

	@Test
	void checks_afterApply_bothValidated() {
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT conname, convalidated FROM pg_constraint"
				+ " WHERE contype = 'c' AND conrelid = 'listing_revisions'::regclass AND conname IN (?, ?)"
				+ " ORDER BY conname", REPLACED, WENT_LIVE);

		assertEquals(List.of(REPLACED, WENT_LIVE), rows.stream().map(row -> (String) row.get("conname")).toList());
		for (Map<String, Object> row : rows) {
			assertEquals(true, row.get("convalidated"), String.valueOf(row.get("conname")));
		}
	}

	/** A listings row written with SQL only, on its own agreement; otherwise valid. */
	private String insertListingRow() {
		String id = ids.generateId();
		created.add(id);
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				id, code().value(), ids.generateId(), "LONG_TERM_RENT", ids.generateId(), "UNPUBLISHED",
				ids.generateId(), now, now);
		return id;
	}

	private void insertRevision(String listingId, int number, String status, Timestamp wentLiveAtOrNull,
			Timestamp replacedAtOrNull) {
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at, went_live_at, replaced_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", ids.generateId(),
				listingId, number, status, ids.generateId(), now, now, wentLiveAtOrNull, replacedAtOrNull);
	}

	/** One case: the insert is refused by the named constraint. */
	private static Executable refused(String constraint, Runnable insert) {
		return () -> {
			DataIntegrityViolationException refusal =
					assertThrows(DataIntegrityViolationException.class, insert::run, constraint);
			assertTrue(String.valueOf(refusal.getMostSpecificCause().getMessage()).contains(constraint), constraint);
		};
	}

	/** A time from the JVM clock (testing.md), truncated to what PostgreSQL stores. */
	private static Timestamp time() {
		return Timestamp.from(now());
	}

	private static ListingCode code() {
		return ListingCode.draw(RANDOM);
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
