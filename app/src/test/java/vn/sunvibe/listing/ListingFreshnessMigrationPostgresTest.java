package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The freshness migrations (listing-publish E06-S3 spec 5.3, E06S3-D6, FR-3, FR-9, FR-16; plan E06S3-P12) on a
 * PostgreSQL of their own that held listings of every stored state, with their revisions and history, before them: the
 * database is migrated to the last version before this slice, filled, then migrated to the latest one. Every earlier
 * listing reads no freshness, the CHECKs refuse their bad rows, every new constraint and index is valid by name, and a
 * listing's records go with it. Without Spring; needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingFreshnessMigrationPostgresTest {

	/** The last version before this slice. */
	private static final String PREVIOUS_VERSION = "202611201102";

	private static final List<String> MIGRATIONS = List.of("V202611211000__create_listing_freshness_records.sql",
			"V202611211001__add_listing_freshness_columns.sql", "V202611211002__validate_listing_freshness_check.sql",
			"V202611211003__index_listings_freshness_due.sql");

	private static final List<String> STORED_STATES = List.of("UNPUBLISHED", "PUBLISHED", "PAUSED",
			"URGENTLY_UNPUBLISHED", "ARCHIVED");

	/** The alphabet of a listing code (listing-detail BR-15). */
	private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");
	private static final String SALE = "01FRESHMIGSALE000000000001";

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(1);
	private final List<String> earlierListings = new ArrayList<>();
	private final List<String> created = new ArrayList<>();
	private JdbcTemplate jdbc;

	@BeforeAll
	void migrateListingsOfEveryStateThenFreshness() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(PREVIOUS_VERSION).load()
				.migrate();
		Timestamp now = Timestamp.from(Instant.now());
		for (String state : STORED_STATES) {
			String listing = id();
			jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
					+ " created_at, updated_at) VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, ?, ?, ?, ?)", listing, code(), id(),
					id(), state, SALE, now, now);
			jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
					+ " updated_at) VALUES (?, ?, 1, 'DRAFT', ?, ?, ?)", id(), listing, SALE, now, now);
			jdbc.update("INSERT INTO listing_history (id, listing_id, action, revision_no, actor_user_id, occurred_at)"
					+ " VALUES (?, ?, 'CREATED', 1, ?, ?)", id(), listing, SALE, now);
			earlierListings.add(listing);
		}

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	/** Children first: the records of every listing a test wrote, then the listing. */
	@AfterEach
	void deleteRows() {
		for (String listing : created) {
			jdbc.update("DELETE FROM listing_freshness_records WHERE listing_id = ?", listing);
			jdbc.update("DELETE FROM listings WHERE id = ?", listing);
		}
		created.clear();
	}

	@Test
	void migrate_populatedListings_columnsNullAndCheckValidated() {
		assertEquals(List.of(true), jdbc.queryForList("SELECT convalidated FROM pg_constraint"
				+ " WHERE conname = 'ck_listings_freshness' AND conrelid = 'listings'::regclass", Boolean.class));

		List<String> expected = earlierListings.stream().map(listing -> listing + " NULL NULL").toList();
		assertEquals(expected, jdbc.queryForList("SELECT id || ' ' || coalesce(freshness_confirmed_on::text, 'NULL')"
				+ " || ' ' || coalesce(freshness_latest_valid::text, 'NULL') FROM listings WHERE id IN (?, ?, ?, ?, ?)"
				+ " ORDER BY id", String.class, earlierListings.toArray()));
	}

	@Test
	void checks_badRows_refused() {
		String listing = insertListing("UNPUBLISHED", null, null);

		List<String> refusals = new ArrayList<>();
		refusals.add(refusedBy(() -> insertRecord(listing, 1, "UNREACHABLE", true, true, false)));
		refusals.add(refusedBy(() -> insertRecord(listing, 2, "AVAILABLE", true, null, false)));
		refusals.add(refusedBy(() -> insertRecord(listing, 3, "AVAILABLE", true, false, true)));
		refusals.add(refusedBy(() -> insertRecord(listing, 0, "NOT_AVAILABLE", null, null, false)));
		refusals.add(refusedBy(() -> insertListing("UNPUBLISHED", null, true)));
		refusals.add(refusedBy(() -> insertListing("UNPUBLISHED", LocalDate.of(2027, 3, 1), null)));

		assertEquals(List.of("ck_listing_freshness_records_answers", "ck_listing_freshness_records_answers",
				"ck_listing_freshness_records_valid", "ck_listing_freshness_records_row_version",
				"ck_listings_freshness", "ck_listings_freshness"), refusals);
	}

	@Test
	void objects_validAndReady_byName() {
		assertEquals(List.of("ck_listing_freshness_records_answers true", "ck_listing_freshness_records_channel true",
						"ck_listing_freshness_records_outcome true", "ck_listing_freshness_records_row_version true",
						"ck_listing_freshness_records_valid true", "fk_listing_freshness_records_listing true",
						"listing_freshness_records_pkey true"),
				jdbc.queryForList("SELECT conname || ' ' || convalidated::text FROM pg_constraint"
						+ " WHERE conrelid = 'listing_freshness_records'::regclass ORDER BY conname", String.class));
		assertEquals(List.of(true), jdbc.queryForList("SELECT convalidated FROM pg_constraint"
				+ " WHERE conname = 'ck_listings_freshness' AND conrelid = 'listings'::regclass", Boolean.class));
		assertEquals(List.of("valid and ready"), validAndReady("uq_listing_freshness_records_version"));
		assertEquals(List.of("valid and ready"), validAndReady("idx_listings_freshness_due"));

		String definition = jdbc.queryForList("SELECT pg_get_indexdef(c.oid) FROM pg_class c"
				+ " WHERE c.relname = 'idx_listings_freshness_due'", String.class).getFirst();
		assertEquals(List.of(true, true, true), List.of(definition.contains("(freshness_confirmed_on, id)"),
				definition.contains("<> 'ARCHIVED'"), definition.contains("freshness_confirmed_on IS NOT NULL")),
				definition);
	}

	@Test
	void deleteListing_cascadesItsRecords() {
		String listing = insertListing("UNPUBLISHED", null, null);
		insertRecord(listing, 1, "NOT_AVAILABLE", null, null, false);

		jdbc.update("DELETE FROM listings WHERE id = ?", listing);

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM listing_freshness_records WHERE listing_id = ?",
				Integer.class, listing));
	}

	@Test
	void uniqueVersion_secondRecordSameRowVersion_refused() {
		String listing = insertListing("UNPUBLISHED", null, null);
		insertRecord(listing, 1, "AVAILABLE", true, true, true);

		assertEquals("uq_listing_freshness_records_version",
				refusedBy(() -> insertRecord(listing, 1, "UNREACHABLE", null, null, false)));
	}

	@Test
	void migrations_eachFile_setsLockTimeoutFirst() throws IOException {
		for (String migration : MIGRATIONS) {
			try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/migration/" + migration)) {
				assertNotNull(in, migration);
				String firstStatement = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
						.map(String::strip)
						.filter(line -> !line.isEmpty() && !line.startsWith("--"))
						.findFirst()
						.orElse("");
				assertEquals("SET LOCAL lock_timeout = '5s';", firstStatement, migration);
			}
		}
	}

	private List<String> validAndReady(String indexName) {
		return jdbc.queryForList("SELECT CASE WHEN i.indisvalid AND i.indisready THEN 'valid and ready' ELSE 'not'"
				+ " END FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE c.relname = ?", String.class,
				indexName);
	}

	/** A listing row of its own agreement with the two freshness columns given. */
	private String insertListing(String state, LocalDate confirmedOnOrNull, Boolean latestValidOrNull) {
		String listing = id();
		created.add(listing);
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at, freshness_confirmed_on, freshness_latest_valid)"
						+ " VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, ?, ?, ?, ?, ?, ?)", listing, code(), id(), id(), state,
				SALE, now, now, confirmedOnOrNull == null ? null : Date.valueOf(confirmedOnOrNull), latestValidOrNull);
		return listing;
	}

	private void insertRecord(String listingId, long rowVersion, String outcome, Boolean termsStillApply,
							  Boolean unitUnchanged, boolean valid) {
		jdbc.update("INSERT INTO listing_freshness_records (id, listing_id, listing_row_version, contact_date,"
						+ " recorded_at, recorded_by, channel, outcome, terms_still_apply, unit_unchanged, valid, note)"
						+ " VALUES (?, ?, ?, DATE '2027-03-01', ?, ?, 'CALL', ?, ?, ?, ?, NULL)", id(), listingId,
				rowVersion, Timestamp.from(Instant.now()), SALE, outcome, termsStillApply, unitUnchanged, valid);
	}

	/** The name of the constraint that refused the write, or null when it was accepted. */
	private static String refusedBy(Runnable write) {
		try {
			write.run();
			return null;
		} catch (DataAccessException e) {
			Matcher name = CONSTRAINT_NAME.matcher(String.valueOf(e.getMostSpecificCause().getMessage()));
			return name.find() ? name.group(1) : e.getMostSpecificCause().getMessage();
		}
	}

	private String id() {
		return String.format("01FRESHMIG%016d", sequence.getAndIncrement());
	}

	/** A listing code no other row of this database holds: TD-FRSH- and four letters of the sequence. */
	private String code() {
		int n = sequence.getAndIncrement();
		StringBuilder suffix = new StringBuilder();
		for (int digit = 0; digit < 4; digit++) {
			suffix.insert(0, CODE_ALPHABET.charAt(n % CODE_ALPHABET.length()));
			n /= CODE_ALPHABET.length();
		}
		return "TD-FRSH-" + suffix;
	}
}
