package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The unit and ownership migrations on a real PostgreSQL (property-units US-1, US-2, BR-7, BR-10, BR-13, BR-14,
 * E-2, E-3; spec section 4, W8-1..W8-9). The database is migrated to the last version before them and filled the
 * way property create left it: one property per legacy status, one with an unknown status, one whose owner id matches
 * no account, one created late in the evening, one with 0 bedrooms and 0 bathrooms, and one property_availability
 * row. It is then migrated to the latest version. Needs Docker, like {@link FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyUnitMigrationPostgresTest {

	private static final String LAST_VERSION_BEFORE_W8 = "202610160901";
	private static final String BACKFILL_SCRIPT =
			"db/migration/V202610161003__backfill_units_and_property_ownerships.sql";
	private static final String ULID = "^[0-9A-HJKMNP-TV-Z]{26}$";

	private static final String OWNER_A = "01OWNERA000000000000000001";
	private static final String OWNER_B = "01OWNERB000000000000000001";
	/** A 36-character owner id in the old format (a UUID); no account has it. */
	private static final String OWNER_WITHOUT_ACCOUNT = "7f9c2b1e-3d4a-4c5b-9e6f-0a1b2c3d4e5f";

	private static final String INSERT_PROPERTY = "INSERT INTO properties (id, property_code, title, street, ward,"
			+ " district, city, property_type, bedrooms, bathrooms, square_meters, monthly_price, deposit_price,"
			+ " min_rent_months, status, owner_id, created_at, updated_at, created_by, updated_by)"
			+ " VALUES (?, ?, 'W8 migration', '1 Bạch Đằng', 'Thạch Thang', 'Hải Châu', 'Đà Nẵng', ?, ?, ?, ?,"
			+ " 5000000, 5000000, 6, ?, ?, ?, ?, '0900000001', '0900000001')";

	/** A property as it was stored before the unit and ownership migrations. */
	private record LegacyProperty(String code, String status, String propertyType, int bedrooms, int bathrooms,
			double squareMeters, String ownerId, LocalDateTime createdAt, LocalDateTime updatedAt) {

		/** A property never updated since it was created. */
		LegacyProperty(String code, String status, String propertyType, int bedrooms, int bathrooms,
				double squareMeters, String ownerId, LocalDateTime createdAt) {
			this(code, status, propertyType, bedrooms, bathrooms, squareMeters, ownerId, createdAt, createdAt);
		}
	}

	private static final List<LegacyProperty> PROPERTIES = List.of(
			new LegacyProperty("WT-MIG-001", "AVAILABLE", "STUDIO", 1, 1, 35.5, OWNER_A,
					LocalDateTime.of(2026, 3, 15, 23, 30)),
			new LegacyProperty("WT-MIG-002", "DRAFT", "ONE_BEDROOM", 1, 1, 45.0, OWNER_A,
					LocalDateTime.of(2026, 3, 16, 8, 0), LocalDateTime.of(2026, 3, 18, 8, 0)),
			new LegacyProperty("WT-MIG-003", "PENDING_APPROVAL", "TWO_BEDROOM", 2, 2, 70.0, OWNER_A,
					LocalDateTime.of(2026, 3, 17, 8, 0)),
			new LegacyProperty("WT-MIG-004", "RENTED", "THREE_BEDROOM", 3, 2, 95.0, OWNER_B,
					LocalDateTime.of(2026, 3, 18, 8, 0)),
			new LegacyProperty("WT-MIG-005", "UNDER_MAINTENANCE", "FOUR_BEDROOM", 4, 3, 120.0, OWNER_B,
					LocalDateTime.of(2026, 3, 19, 8, 0)),
			new LegacyProperty("WT-MIG-006", "UNAVAILABLE", "DUPLEX", 3, 3, 150.0, OWNER_B,
					LocalDateTime.of(2026, 3, 20, 8, 0)),
			new LegacyProperty("WT-MIG-007", "SOLD", "PENTHOUSE", 4, 4, 200.0, OWNER_B,
					LocalDateTime.of(2026, 3, 21, 8, 0)),
			new LegacyProperty("WT-MIG-008", "ARCHIVED_X", "STUDIO", 1, 1, 30.0, OWNER_B,
					LocalDateTime.of(2026, 3, 22, 8, 0)),
			new LegacyProperty("WT-MIG-009", "AVAILABLE", "STUDIO", 0, 0, 25.0, OWNER_WITHOUT_ACCOUNT,
					LocalDateTime.of(2026, 3, 23, 8, 0)));

	/** Property code -> availability state of its unit (property-units BR-7). */
	private static final Map<String, String> EXPECTED_STATE = Map.of(
			"WT-MIG-001", "AVAILABLE",
			"WT-MIG-002", "AVAILABLE",
			"WT-MIG-003", "AVAILABLE",
			"WT-MIG-004", "OCCUPIED",
			"WT-MIG-005", "MAINTENANCE",
			"WT-MIG-006", "BLOCKED",
			"WT-MIG-007", "BLOCKED",
			"WT-MIG-008", "BLOCKED",
			"WT-MIG-009", "AVAILABLE");

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private JdbcTemplate jdbc;
	private Flyway flywayToLatest;
	private List<Map<String, Object>> propertiesBefore;
	private List<Map<String, Object>> availabilityBefore;

	@BeforeAll
	void migrateOldDatabaseThenLatest() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.target(LAST_VERSION_BEFORE_W8).load().migrate();

		// What property create left before the unit and ownership migrations: properties without a unit or an ownership, no version column
		for (LegacyProperty p : PROPERTIES) {
			jdbc.update(INSERT_PROPERTY, UUID.randomUUID().toString(), p.code(), p.propertyType(), p.bedrooms(),
					p.bathrooms(), p.squareMeters(), p.status(), p.ownerId(), p.createdAt(), p.updatedAt());
		}
		assertEquals(1, jdbc.update("INSERT INTO property_availability (id, available_from_date, property_id, status)"
				+ " SELECT ?, DATE '2026-01-01', id, 'BOOKED' FROM properties WHERE property_code = 'WT-MIG-004'",
				UUID.randomUUID().toString()));
		propertiesBefore = jdbc.queryForList("SELECT * FROM properties ORDER BY id");
		availabilityBefore = jdbc.queryForList("SELECT * FROM property_availability ORDER BY id");

		flywayToLatest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
		flywayToLatest.migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	/** property-units US-1 AC-1, BR-3 (W8-1) */
	@Test
	void migrate_existingProperties_createsOneUnitPerProperty() {
		assertEquals(PROPERTIES.size(), count("units"));
		assertEquals(0, propertiesWithoutExactlyOneRowIn("units"));
	}

	/** property-units US-1 AC-2, BR-4, BR-5 (W8-2): the canonical unit copies code, type, rooms and area; 0 rooms are allowed. */
	@Test
	void migrate_existingProperties_unitCopiesCodeTypeRoomsAndArea() {
		for (LegacyProperty p : PROPERTIES) {
			Map<String, Object> unit = unitOf(p.code());

			assertEquals(p.code(), unit.get("unit_code"), p.code());
			assertEquals(p.propertyType(), unit.get("unit_type"), p.code());
			assertEquals(p.bedrooms(), unit.get("bedrooms"), p.code());
			assertEquals(p.bathrooms(), unit.get("bathrooms"), p.code());
			assertEquals(p.squareMeters(), unit.get("area_sqm"), p.code());
		}
		assertEquals(35.5, unitOf("WT-MIG-001").get("area_sqm"));
		assertEquals(0, unitOf("WT-MIG-009").get("bedrooms"));
		assertEquals(0, unitOf("WT-MIG-009").get("bathrooms"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM units u JOIN properties p ON p.id = u.property_id"
				+ " WHERE u.unit_code <> p.property_code OR u.unit_type <> p.property_type OR u.bedrooms <> p.bedrooms"
				+ " OR u.bathrooms <> p.bathrooms OR u.area_sqm <> p.square_meters", Integer.class));
	}

	/** property-units US-1 AC-3, BR-7, E-2 (W8-3): the legacy status decides the state; an unknown status gives BLOCKED. */
	@Test
	void migrate_legacyStatus_mapsToTheAvailabilityState() {
		for (LegacyProperty p : PROPERTIES) {
			assertEquals(EXPECTED_STATE.get(p.code()), unitOf(p.code()).get("availability_state"),
					p.code() + " (" + p.status() + ")");
		}
	}

	/**
	 * property-units US-2 AC-1, AC-2, BR-10, C-14 (W8-4): the owner is linked from the property's creation date (never its update
	 * date), with no end.
	 */
	@Test
	void migrate_existingProperties_createsOneUnverifiedOwnershipPerProperty() {
		assertEquals(PROPERTIES.size(), count("property_ownerships"));
		assertEquals(0, propertiesWithoutExactlyOneRowIn("property_ownerships"));

		for (LegacyProperty p : PROPERTIES) {
			Map<String, Object> ownership = ownershipOf(p.code());

			assertEquals(p.ownerId(), ownership.get("user_id"), p.code());
			assertEquals("OWNER", ownership.get("relation_type"), p.code());
			assertEquals("UNVERIFIED", ownership.get("verification_state"), p.code());
			assertNull(ownership.get("valid_to"), p.code());
		}
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM property_ownerships o JOIN properties p"
				+ " ON p.id = o.property_id WHERE o.valid_from <> CAST(p.created_at AS DATE)", Integer.class));
		// WT-MIG-002 was updated two days after it was created: valid_from is the creation date
		assertEquals(LocalDate.of(2026, 3, 16), jdbc.queryForObject("SELECT o.valid_from FROM property_ownerships o"
				+ " JOIN properties p ON p.id = o.property_id WHERE p.property_code = 'WT-MIG-002'", LocalDate.class));
		// Created at 23:30: the date stays the local one, it is not moved by a time zone
		assertEquals(LocalDate.of(2026, 3, 15), jdbc.queryForObject("SELECT o.valid_from FROM property_ownerships o"
				+ " JOIN properties p ON p.id = o.property_id WHERE p.property_code = 'WT-MIG-001'", LocalDate.class));
	}

	/** property-units E-3 (W8-5): an owner_id that matches no account, 36 characters long, is copied as is. */
	@Test
	void migrate_ownerWithoutAccount_copiesTheOwnerIdAsIs() {
		assertEquals("7f9c2b1e-3d4a-4c5b-9e6f-0a1b2c3d4e5f", ownershipOf("WT-MIG-009").get("user_id"));
	}

	/** property-units US-1 AC-4, BR-13 (W8-6): legacy columns and property_availability rows are as they were; version is new. */
	@Test
	void migrate_existingProperties_leavesLegacyDataUnchanged() {
		List<Map<String, Object>> propertiesAfter = jdbc.queryForList("SELECT * FROM properties ORDER BY id");
		propertiesAfter.forEach(row -> assertNotNull(row.remove("version"), "properties.version is new in W8"));

		assertEquals(propertiesBefore, propertiesAfter);
		assertEquals(availabilityBefore, jdbc.queryForList("SELECT * FROM property_availability ORDER BY id"));
	}

	/** property-units BR-14 (W8-7) */
	@Test
	void migrate_existingProperties_startsVersionsAtZero() {
		assertEquals(List.of(0L), jdbc.queryForList("SELECT DISTINCT version FROM properties", Long.class));
		assertEquals(List.of(0L), jdbc.queryForList("SELECT DISTINCT version FROM units", Long.class));
	}

	/** ADR-006 (W8-8): the SQL ULIDs have the right format and never repeat. */
	@Test
	void migrate_existingProperties_givesUniqueUlidIds() {
		List<String> ids = new ArrayList<>(jdbc.queryForList("SELECT id FROM units", String.class));
		ids.addAll(jdbc.queryForList("SELECT id FROM property_ownerships", String.class));

		assertEquals(2 * PROPERTIES.size(), ids.size());
		assertEquals(ids.size(), Set.copyOf(ids).size());
		for (String id : ids) {
			assertTrue(id.matches(ULID), () -> "not a ULID: " + id);
		}
	}

	/** C-13, anti-goals (W8-9): the script is repeatable, and the temporary ULID function is gone. */
	@Test
	void backfillScript_runAgain_changesNothingAndLeavesNoFunction() throws IOException {
		List<Map<String, Object>> unitsBefore = jdbc.queryForList("SELECT * FROM units ORDER BY id");
		List<Map<String, Object>> ownershipsBefore = jdbc.queryForList("SELECT * FROM property_ownerships ORDER BY id");
		assertEquals(0, tempUlidFunctionCount(), "left behind by the Flyway run");

		jdbc.execute(script(BACKFILL_SCRIPT));

		assertEquals(PROPERTIES.size(), count("units"));
		assertEquals(PROPERTIES.size(), count("property_ownerships"));
		assertEquals(unitsBefore, jdbc.queryForList("SELECT * FROM units ORDER BY id"));
		assertEquals(ownershipsBefore, jdbc.queryForList("SELECT * FROM property_ownerships ORDER BY id"));
		assertEquals(0, tempUlidFunctionCount(), "left behind by the second run");
		assertEquals(0, flywayToLatest.info().pending().length);
	}

	/** C-13 (W8-9): a property that lost its rows gets exactly its own back; the other rows are not touched. */
	@Test
	void backfillScript_propertyWithoutRows_getsExactlyItsMissingRows() throws IOException {
		String propertyId = jdbc.queryForObject(
				"SELECT id FROM properties WHERE property_code = 'WT-MIG-005'", String.class);
		String oldUnitId = unitOf("WT-MIG-005").get("id").toString();
		String oldOwnershipId = ownershipOf("WT-MIG-005").get("id").toString();
		List<Map<String, Object>> otherUnits = jdbc.queryForList(
				"SELECT * FROM units WHERE property_id <> ? ORDER BY id", propertyId);
		List<Map<String, Object>> otherOwnerships = jdbc.queryForList(
				"SELECT * FROM property_ownerships WHERE property_id <> ? ORDER BY id", propertyId);
		assertEquals(1, jdbc.update("DELETE FROM units WHERE property_id = ?", propertyId));
		assertEquals(1, jdbc.update("DELETE FROM property_ownerships WHERE property_id = ?", propertyId));

		jdbc.execute(script(BACKFILL_SCRIPT));

		assertEquals(PROPERTIES.size(), count("units"));
		assertEquals(PROPERTIES.size(), count("property_ownerships"));
		Map<String, Object> unit = unitOf("WT-MIG-005");
		assertTrue(unit.get("id").toString().matches(ULID));
		assertNotEquals(oldUnitId, unit.get("id"));
		assertEquals("WT-MIG-005", unit.get("unit_code"));
		assertEquals("FOUR_BEDROOM", unit.get("unit_type"));
		assertEquals(4, unit.get("bedrooms"));
		assertEquals(3, unit.get("bathrooms"));
		assertEquals(120.0, unit.get("area_sqm"));
		assertEquals("MAINTENANCE", unit.get("availability_state"));
		assertEquals(0L, unit.get("version"));
		Map<String, Object> ownership = ownershipOf("WT-MIG-005");
		assertTrue(ownership.get("id").toString().matches(ULID));
		assertNotEquals(oldOwnershipId, ownership.get("id"));
		assertEquals(OWNER_B, ownership.get("user_id"));
		assertEquals("OWNER", ownership.get("relation_type"));
		assertEquals("UNVERIFIED", ownership.get("verification_state"));
		assertEquals(LocalDate.of(2026, 3, 19), jdbc.queryForObject("SELECT valid_from FROM property_ownerships"
				+ " WHERE property_id = ?", LocalDate.class, propertyId));
		assertNull(ownership.get("valid_to"));
		assertEquals(otherUnits, jdbc.queryForList("SELECT * FROM units WHERE property_id <> ? ORDER BY id", propertyId));
		assertEquals(otherOwnerships, jdbc.queryForList(
				"SELECT * FROM property_ownerships WHERE property_id <> ? ORDER BY id", propertyId));
		assertEquals(0, tempUlidFunctionCount());
	}

	private Map<String, Object> unitOf(String propertyCode) {
		return jdbc.queryForMap("SELECT u.* FROM units u JOIN properties p ON p.id = u.property_id"
				+ " WHERE p.property_code = ?", propertyCode);
	}

	private Map<String, Object> ownershipOf(String propertyCode) {
		return jdbc.queryForMap("SELECT o.* FROM property_ownerships o JOIN properties p ON p.id = o.property_id"
				+ " WHERE p.property_code = ?", propertyCode);
	}

	/** Number of properties that do not have exactly one row in a table with a property_id column. */
	private int propertiesWithoutExactlyOneRowIn(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM properties p WHERE"
				+ " (SELECT count(*) FROM " + table + " t WHERE t.property_id = p.id) <> 1", Integer.class);
	}

	private int tempUlidFunctionCount() {
		return jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE proname = 'w8_tmp_ulid'", Integer.class);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private static String script(String path) throws IOException {
		return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
	}
}
