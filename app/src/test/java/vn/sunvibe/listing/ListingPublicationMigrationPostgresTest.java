package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The publication migrations of listing-publish E06-S7 (spec 5.3, E06S7-D2, E06S7-D11; criterion PB-12, its column
 * part) on a PostgreSQL of their own that held a listing and its revision before them: the database is migrated to the
 * last version before this slice, filled, then migrated to the latest one. The two tables hold exactly the public and
 * internal columns of E06S7-D2 and no jsonb, every constraint and index is valid, the CHECKs refuse their bad rows by
 * name, the earlier revision reads {@code fee_names_checked = false}, and a listing's publication and fee rows go with
 * it. Without Spring; needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingPublicationMigrationPostgresTest {

	/** The last version before this slice. */
	private static final String PREVIOUS_VERSION = "202611230901";

	private static final List<String> MIGRATIONS = List.of("V202611271000__create_listing_publications.sql",
			"V202611271001__create_listing_publication_fees.sql",
			"V202611271002__add_listing_revision_fee_names_checked.sql");

	/** E06S7-D2: the public columns (listing-publish BR-14 groups 1 to 7 and 9 without photos). */
	private static final List<String> PUBLIC_COLUMNS = List.of("code", "transaction_type", "property_type",
			"province_code", "ward_code", "bedrooms", "bathrooms", "area_sqm", "monthly_rent", "deposit_basis",
			"deposit_months", "deposit_amount", "minimum_term_months", "payment_period_months", "title", "description",
			"feature_furnishing", "feature_air_conditioner", "feature_water_heater", "feature_fridge",
			"feature_washing_machine", "feature_kitchen", "feature_balcony", "feature_elevator",
			"feature_motorbike_parking", "feature_car_parking", "feature_internet", "feature_pets",
			"feature_no_shared_landlord", "went_live_at");
	/** E06S7-D2: the internal columns, never in an answer. */
	private static final List<String> INTERNAL_COLUMNS = List.of("listing_id", "unit_id", "agreement_id",
			"terms_digest", "row_version");
	private static final List<String> FEE_COLUMNS = List.of("listing_id", "fee_no", "name", "basis", "amount",
			"charge_unit");

	private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");
	private static final String SALE = "01PUBMIGSALE00000000000001";
	private static final String DIGEST = "0".repeat(64);

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(1);
	private final List<String> created = new ArrayList<>();
	private JdbcTemplate jdbc;
	private String earlierRevision;

	@BeforeAll
	void migrateAListingAndItsRevisionThenThePublication() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(PREVIOUS_VERSION).load()
				.migrate();
		String listing = insertListing();
		earlierRevision = id();
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at) VALUES (?, ?, 1, 'DRAFT', ?, ?, ?)", earlierRevision, listing, SALE, now, now);
		// the earlier listing stays for the whole class
		created.clear();

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	/** The listings a test wrote; their publication and fee rows go by the cascades. */
	@AfterEach
	void deleteRows() {
		for (String listing : created) {
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", listing);
			jdbc.update("DELETE FROM listings WHERE id = ?", listing);
		}
		created.clear();
	}

	/** PB-12 (E06S7-D2, ADR-012 decision 1): exactly the public and internal columns, the fee list, no jsonb. */
	@Test
	void columns_exactlyTheListsNoJsonb() {
		List<String> publication = new ArrayList<>(PUBLIC_COLUMNS);
		publication.addAll(INTERNAL_COLUMNS);

		assertEquals(publication.stream().sorted().toList(), columnsOf("listing_publications"));
		assertEquals(FEE_COLUMNS.stream().sorted().toList(), columnsOf("listing_publication_fees"));
		assertEquals(List.of(), jdbc.queryForList("SELECT table_name || '.' || column_name FROM information_schema.columns"
				+ " WHERE table_schema = current_schema() AND table_name IN ('listing_publications',"
				+ " 'listing_publication_fees') AND data_type IN ('jsonb', 'json')", String.class));
	}

	/** Every constraint of the two tables validated and both indexes valid and ready. */
	@Test
	void constraintsAndIndexes_allValid() {
		assertEquals(List.of(), jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid IN"
				+ " ('listing_publications'::regclass, 'listing_publication_fees'::regclass) AND NOT convalidated",
				String.class));
		assertEquals(List.of(), jdbc.queryForList("SELECT c.relname FROM pg_index i JOIN pg_class c"
				+ " ON c.oid = i.indexrelid WHERE i.indrelid IN ('listing_publications'::regclass,"
				+ " 'listing_publication_fees'::regclass) AND NOT (i.indisvalid AND i.indisready)", String.class));
		assertEquals(List.of("ix_listing_publications_unit", "listing_publications_pkey",
						"uq_listing_publications_code"),
				jdbc.queryForList("SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid"
						+ " WHERE i.indrelid = 'listing_publications'::regclass ORDER BY c.relname", String.class));
	}

	/** E06S7-D11: a revision stored before the check reads false. */
	@Test
	void earlierRevision_feeNamesCheckedFalse() {
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT fee_names_checked FROM listing_revisions WHERE id = ?",
				Boolean.class, earlierRevision));
	}

	/** E06S7-D2: a listing's publication and its fee rows go with it. */
	@Test
	void deleteListing_cascadesPublicationAndFees() {
		String listing = insertListing();
		insertPublication(listing, Map.of());
		insertFee(listing, 1);

		jdbc.update("DELETE FROM listings WHERE id = ?", listing);

		assertEquals(List.of(0, 0), List.of(
				count("SELECT count(*) FROM listing_publications WHERE listing_id = ?", listing),
				count("SELECT count(*) FROM listing_publication_fees WHERE listing_id = ?", listing)));
	}

	/** The CHECKs of spec 5.3, each refusing its bad row by name. */
	@Test
	void checks_badRows_refusedByName() {
		List<String> refusals = new ArrayList<>();
		refusals.add(refusedBy(Map.of("province_code", "48")));
		refusals.add(refusedBy(Map.of("property_type", "CASTLE")));
		refusals.add(refusedBy(Map.of("deposit_basis", "MONTHS", "deposit_months", 2, "deposit_amount", 100)));
		refusals.add(refusedBy(Map.of("transaction_type", "SHORT_TERM_RENT")));
		refusals.add(refusedBy(Map.of("terms_digest", "0".repeat(63))));
		refusals.add(refusedBy(Map.of("row_version", -1)));

		assertEquals(List.of("ck_listing_publications_place", "ck_listing_publications_property_type",
				"ck_listing_publications_deposit", "ck_listing_publications_transaction_type",
				"ck_listing_publications_terms_digest", "ck_listing_publications_row_version"), refusals);
	}

	/** The fee numbers of E05's order, 1 to 20. */
	@Test
	void feeNo_outsideOneToTwenty_refused() {
		String listing = insertListing();
		insertPublication(listing, Map.of());

		assertEquals(List.of("ck_listing_publication_fees_fee_no", "ck_listing_publication_fees_fee_no"),
				List.of(nameOf(() -> insertFee(listing, 0)), nameOf(() -> insertFee(listing, 21))));
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

	private List<String> columnsOf(String table) {
		return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema()"
				+ " AND table_name = ? ORDER BY column_name", String.class, table);
	}

	/** The name of the constraint that refused a publication row of a new listing with the values given. */
	private String refusedBy(Map<String, Object> values) {
		String listing = insertListing();
		return nameOf(() -> insertPublication(listing, values));
	}

	/** A publication row whose every column holds a valid value, except those given. */
	private void insertPublication(String listingId, Map<String, Object> overrides) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("listing_id", listingId);
		row.put("code", jdbc.queryForObject("SELECT code FROM listings WHERE id = ?", String.class, listingId));
		row.put("transaction_type", "LONG_TERM_RENT");
		row.put("property_type", "HOUSE");
		row.put("province_code", null);
		row.put("ward_code", null);
		row.put("bedrooms", 1);
		row.put("bathrooms", 1);
		row.put("area_sqm", 20.0);
		row.put("monthly_rent", 8_000_000);
		row.put("deposit_basis", "MONTHS");
		row.put("deposit_months", 2);
		row.put("deposit_amount", null);
		row.put("minimum_term_months", 6);
		row.put("payment_period_months", 1);
		row.put("title", "Phòng trọ mới, sạch sẽ gần chợ Hàn");
		row.put("description", "Phòng rộng.");
		row.put("feature_furnishing", null);
		for (String feature : List.of("feature_air_conditioner", "feature_water_heater", "feature_fridge",
				"feature_balcony", "feature_elevator", "feature_motorbike_parking", "feature_car_parking",
				"feature_internet", "feature_no_shared_landlord")) {
			row.put(feature, false);
		}
		row.put("feature_washing_machine", null);
		row.put("feature_kitchen", null);
		row.put("feature_pets", null);
		row.put("went_live_at", Timestamp.from(Instant.now()));
		row.put("unit_id", id());
		row.put("agreement_id", id());
		row.put("terms_digest", DIGEST);
		row.put("row_version", 0);
		row.putAll(overrides);
		jdbc.update("INSERT INTO listing_publications (" + String.join(", ", row.keySet()) + ") VALUES ("
				+ String.join(", ", row.keySet().stream().map(key -> "?").toList()) + ")", row.values().toArray());
	}

	private void insertFee(String listingId, int feeNo) {
		jdbc.update("INSERT INTO listing_publication_fees (listing_id, fee_no, name, basis, amount, charge_unit)"
				+ " VALUES (?, ?, 'Điện', 'AMOUNT', 3500, 'PER_KWH')", listingId, feeNo);
	}

	private String insertListing() {
		String listing = id();
		created.add(listing);
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
				+ " created_at, updated_at) VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, 'PUBLISHED', ?, ?, ?)", listing, code(),
				id(), id(), SALE, now, now);
		return listing;
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	/** The name of the constraint that refused the write, or null when it was accepted. */
	private static String nameOf(Runnable write) {
		try {
			write.run();
			return null;
		} catch (DataAccessException e) {
			Matcher name = CONSTRAINT_NAME.matcher(String.valueOf(e.getMostSpecificCause().getMessage()));
			return name.find() ? name.group(1) : e.getMostSpecificCause().getMessage();
		}
	}

	private String id() {
		return String.format("01PUBMIG%018d", sequence.getAndIncrement());
	}

	/** A listing code no other row of this database holds: TD-PUBM- and four letters of the sequence. */
	private String code() {
		int n = sequence.getAndIncrement();
		StringBuilder suffix = new StringBuilder();
		for (int digit = 0; digit < 4; digit++) {
			suffix.insert(0, CODE_ALPHABET.charAt(n % CODE_ALPHABET.length()));
			n /= CODE_ALPHABET.length();
		}
		return "TD-PUBM-" + suffix;
	}
}
