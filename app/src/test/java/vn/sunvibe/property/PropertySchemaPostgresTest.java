package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The expansion of properties and units for the intake (property-create intake spec 5.2 files 1 to 4, S-15) on a real
 * PostgreSQL. Every refusal asserts the constraint or index by name, so an unrelated NOT NULL cannot stand in for a
 * missing CHECK. The sequence tests seed a database of their own migrated to just before the sequence file, because in a
 * Spring test Flyway runs on empty tables. Needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertySchemaPostgresTest {

	/** The last version before the sequence file (spec 5.2 file 4). */
	private static final String BEFORE_SEQUENCE = "202611061004";

	private static final String LEGACY_COLUMNS_CHECK = "chk_properties_legacy_columns_present";
	private static final String INTAKE_COLUMNS_CHECK = "chk_properties_intake_columns_present";
	private static final String UNIT_CODE_INDEX = "uq_units_property_unit_code_key";
	private static final String INTAKE_ID_INDEX = "uq_properties_intake_id";
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String CHECK_VIOLATION = "23514";

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(100);
	private JdbcTemplate jdbc;

	@BeforeAll
	void migrate() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	// ==================== the intake columns (spec 5.2 file 1) ====================

	@Test
	void schema_intakeRowWithoutAssetType_refused() {
		Map<String, Object> row = intakeProperty();
		row.remove("asset_type");

		assertRefusedBy(CHECK_VIOLATION, INTAKE_COLUMNS_CHECK, () -> insert("properties", row));
	}

	@Test
	void schema_intakeRowWithoutRentalLayout_refused() {
		Map<String, Object> row = intakeProperty();
		row.remove("rental_layout");

		assertRefusedBy(CHECK_VIOLATION, INTAKE_COLUMNS_CHECK, () -> insert("properties", row));
	}

	@Test
	void schema_intakeRowWithoutWardCode_refused() {
		Map<String, Object> row = intakeProperty();
		row.remove("ward_code");

		assertRefusedBy(CHECK_VIOLATION, INTAKE_COLUMNS_CHECK, () -> insert("properties", row));
	}

	@Test
	void schema_intakeRowWithNullLegacyColumns_allowed() {
		Map<String, Object> row = intakeProperty();

		insert("properties", row);

		Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM properties WHERE id = ?", row.get("id"));
		for (String column : LEGACY_COLUMNS) {
			assertTrue(stored.containsKey(column), column);
			assertNull(stored.get(column), column);
		}
	}

	/** spec 5.2 file 1: a row with no intake keeps every legacy column, whichever one is left out. */
	@Test
	void schema_legacyRowWithNullPrice_refused() {
		for (String column : LEGACY_COLUMNS) {
			Map<String, Object> row = legacyProperty();
			row.remove(column);

			assertRefusedBy(CHECK_VIOLATION, LEGACY_COLUMNS_CHECK, () -> insert("properties", row));
		}
	}

	/** spec 5.2: files 1 to 4 suit a running older binary, which fills every legacy column and none of the new ones. */
	@Test
	void schema_legacyInsert_stillWorks() {
		Map<String, Object> row = legacyProperty();

		insert("properties", row);

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM properties WHERE id = ? AND intake_id IS NULL",
				Integer.class, row.get("id")));
	}

	@Test
	void schema_noConstraintLeftUnvalidated() {
		assertEquals(List.of(), jdbc.queryForList("SELECT conrelid::regclass::text || '.' || conname FROM pg_constraint"
				+ " WHERE NOT convalidated", String.class));
	}

	/** spec D5-18: unit_type NULL reads unclassified, so a fake STUDIO is never stored. */
	@Test
	void schema_unitWithNullType_allowed() {
		String property = insertLegacy();

		insert("units", unit(property, "A1"));

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM units WHERE property_id = ? AND unit_type IS NULL",
				Integer.class, property));
	}

	// ==================== the indexes (spec 5.2 file 3, BR-8) ====================

	@Test
	void schema_unitCodeCaseAndTrim_collide() {
		String property = insertLegacy();
		insert("units", unit(property, "P101"));

		assertRefusedBy(UNIQUE_VIOLATION, UNIT_CODE_INDEX, () -> insert("units", unit(property, " p101 ")));
	}

	@Test
	void schema_sameUnitCodeInTwoProperties_allowed() {
		String first = insertLegacy();
		String second = insertLegacy();
		insert("units", unit(first, "P202"));

		insert("units", unit(second, "P202"));

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM units WHERE unit_code = 'P202'", Integer.class));
	}

	@Test
	void schema_intakeIdUnique() {
		Map<String, Object> first = intakeProperty();
		Map<String, Object> second = intakeProperty();
		second.put("intake_id", first.get("intake_id"));
		insert("properties", first);

		assertRefusedBy(UNIQUE_VIOLATION, INTAKE_ID_INDEX, () -> insert("properties", second));
	}

	@Test
	void schema_twoLegacyRowsWithoutIntake_allowed() {
		String first = insertLegacy();
		String second = insertLegacy();

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM properties WHERE id IN (?, ?) AND intake_id IS NULL",
				Integer.class, first, second));
	}

	@Test
	void schema_wardAndAddressIndex_exists() {
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename = 'properties' AND"
				+ " indexname = 'idx_properties_ward_code_address_norm'", Integer.class));
	}

	// ==================== the code sequence (spec 5.2 file 4, A-3) ====================

	@Test
	void sequence_startsAfterHighestCode() {
		inNewDatabase("sequence_after_highest", dataSource -> {
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(BEFORE_SEQUENCE).load()
					.migrate();
			insertLegacyWithCode("SV-HOU-000007");
			insertLegacyWithCode("SV-APT-000042");
			insertLegacyWithCode("DA-APT-000999");

			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

			assertEquals(43L, jdbc.queryForObject("SELECT nextval('property_intake_code_seq')", Long.class));
		});
	}

	@Test
	void sequence_noMatchingCode_startsAtOne() {
		inNewDatabase("sequence_no_match", dataSource -> {
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(BEFORE_SEQUENCE).load()
					.migrate();
			insertLegacyWithCode("DA-APT-000999");

			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

			assertEquals(1L, jdbc.queryForObject("SELECT nextval('property_intake_code_seq')", Long.class));
		});
	}

	// ==================== rows ====================

	private static final List<String> LEGACY_COLUMNS = List.of("status", "monthly_price", "deposit_price",
			"min_rent_months", "property_type", "bedrooms", "bathrooms", "square_meters", "district");

	private void inNewDatabase(String name, Consumer<DriverManagerDataSource> body) {
		jdbc.execute("CREATE DATABASE " + name);
		String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name);
		DriverManagerDataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(),
				postgres.getPassword());
		JdbcTemplate main = jdbc;
		jdbc = new JdbcTemplate(dataSource);
		try {
			body.accept(dataSource);
		} finally {
			jdbc = main;
		}
	}

	private String insertLegacy() {
		Map<String, Object> row = legacyProperty();
		insert("properties", row);
		return row.get("id").toString();
	}

	private void insertLegacyWithCode(String code) {
		Map<String, Object> row = legacyProperty();
		row.put("property_code", code);
		insert("properties", row);
	}

	/** The columns the previous binary writes. */
	private Map<String, Object> legacyProperty() {
		Map<String, Object> row = commonProperty();
		row.put("district", "Hải Châu");
		row.put("property_type", "STUDIO");
		row.put("bedrooms", 1);
		row.put("bathrooms", 1);
		row.put("square_meters", 30.0);
		row.put("monthly_price", new BigDecimal("5000000"));
		row.put("deposit_price", new BigDecimal("5000000"));
		row.put("min_rent_months", 6);
		row.put("status", "AVAILABLE");
		return row;
	}

	private Map<String, Object> intakeProperty() {
		Map<String, Object> row = commonProperty();
		row.put("intake_id", id("INTK"));
		row.put("asset_type", "HOUSE");
		row.put("rental_layout", "WHOLE");
		row.put("ward_code", "20242");
		row.put("address_norm", "1 bach dang");
		return row;
	}

	private Map<String, Object> commonProperty() {
		Map<String, Object> row = new LinkedHashMap<>();
		LocalDateTime now = LocalDateTime.of(2026, 11, 6, 9, 0);
		row.put("id", id("PROP"));
		row.put("property_code", String.format("WT-SCH-%06d", sequence.incrementAndGet()));
		row.put("title", "Whole house, Hải Châu");
		row.put("street", "1 Bạch Đằng");
		row.put("ward", "Hải Châu");
		row.put("city", "Đà Nẵng");
		row.put("owner_id", "01OWNER0000000000000000001");
		row.put("created_at", now);
		row.put("updated_at", now);
		row.put("created_by", "01SALE00000000000000000001");
		row.put("updated_by", "01SALE00000000000000000001");
		return row;
	}

	private Map<String, Object> unit(String property, String code) {
		OffsetDateTime now = OffsetDateTime.of(2026, 11, 6, 9, 0, 0, 0, ZoneOffset.UTC);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id("UNIT"));
		row.put("property_id", property);
		row.put("unit_code", code);
		row.put("bedrooms", 1);
		row.put("bathrooms", 1);
		row.put("area_sqm", 30.0);
		row.put("availability_state", "AVAILABLE");
		row.put("created_at", now);
		row.put("updated_at", now);
		return row;
	}

	// ==================== helpers ====================

	private void insert(String table, Map<String, Object> row) {
		jdbc.update("INSERT INTO " + table + " (" + String.join(", ", row.keySet()) + ") VALUES ("
				+ String.join(", ", Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
	}

	/** The statement fails with this SQLSTATE on this constraint or unique index. */
	private static void assertRefusedBy(String sqlState, String constraint, Runnable statement) {
		try {
			statement.run();
		} catch (DataAccessException e) {
			SQLException failure = sqlCause(e);
			assertEquals(sqlState, failure.getSQLState(), failure.getMessage());
			assertTrue(failure.getMessage().contains("\"" + constraint + "\""), failure.getMessage());
			return;
		}
		fail("the statement was accepted; expected " + constraint);
	}

	private static SQLException sqlCause(DataAccessException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException failure) {
				return failure;
			}
		}
		throw e;
	}

	/** A fresh 26-character id with the marker. */
	private String id(String marker) {
		return String.format("01SCH%s%017d", marker, sequence.incrementAndGet());
	}
}
