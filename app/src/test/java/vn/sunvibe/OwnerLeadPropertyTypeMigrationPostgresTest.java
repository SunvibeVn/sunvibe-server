package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The migration of stored owner leads to the six property types (acquisition-owner-intake BR-3, Q-16) on a real
 * PostgreSQL. The database is migrated to the last version before it and holds one lead per old value plus one
 * without a type, each with its consent record; it is then migrated to the version of that migration. Every unit
 * layout becomes {@code APARTMENT}, {@code TOWNHOUSE} becomes {@code HOUSE}, land and "other" lose their type, and
 * nothing else changes. Needs Docker, like {@link FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerLeadPropertyTypeMigrationPostgresTest {

	private static final String LAST_VERSION_BEFORE = "202610161114";
	/** The migration under test; later migrations add columns of their own and are left out. */
	private static final String VERSION = "202610221000";
	private static final String SCRIPT = "db/migration/V" + VERSION + "__replace_owner_lead_property_types.sql";
	/** The other tables of the acquisition module; the migration leaves them as they are. */
	private static final List<String> OTHER_TABLES = List.of("consent_records", "owner_lead_activities",
			"owner_lead_duplicate_candidates", "owner_lead_access_logs", "owner_intake_idempotency_keys",
			"sale_territories", "sale_territory_changes");

	private static final String ASSIGNEE = "01MIGSALE00000000000000001";
	/** The lead stored first; the others name it as their original and as a duplicate candidate. */
	private static final String FIRST_LEAD = String.format("01MIGLEAD%017d", 1);

	/** Old stored value (null: no type) to the value after the migration. */
	private static final Map<String, String> MAPPING = new LinkedHashMap<>();

	static {
		for (String layout : List.of("STUDIO", "ONE_BEDROOM", "TWO_BEDROOM", "THREE_BEDROOM", "FOUR_BEDROOM", "DUPLEX",
				"PENTHOUSE")) {
			MAPPING.put(layout, "APARTMENT");
		}
		MAPPING.put("TOWNHOUSE", "HOUSE");
		MAPPING.put("LAND", null);
		MAPPING.put("OTHER", null);
		MAPPING.put(null, null);
	}

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private JdbcTemplate jdbc;
	private Flyway flywayToLatest;
	/** Lead id to its old property type. */
	private final Map<String, String> oldTypeById = new HashMap<>();
	private List<Map<String, Object>> leadsBefore;
	private Map<String, List<Map<String, Object>>> otherTablesBefore;

	@BeforeAll
	void migrateOldDatabaseThenLatest() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.target(LAST_VERSION_BEFORE).load().migrate();

		int n = 0;
		for (String oldType : MAPPING.keySet()) {
			n++;
			String consentId = String.format("01MIGCONSENT%014d", n);
			String leadId = String.format("01MIGLEAD%017d", n);
			jdbc.update("INSERT INTO consent_records (id, notice_version, purpose, channel, accepted_at)"
					+ " VALUES (?, 'owner-intake-2026-10', 'OWNER_INTAKE', 'WEB', now())", consentId);
			// Every optional column is set, so a migration that touched another column would show in the comparison
			jdbc.update("INSERT INTO owner_leads (id, request_code, status, intent, source, contact_name,"
					+ " phone_normalized, email, province_code, province_name, ward_code, ward_name, address_detail,"
					+ " address_key, latitude, longitude, property_type, asking_price_amount, asking_price_unit,"
					+ " available_from, notes, preferred_callback_time, consent_record_id, submission_snapshot,"
					+ " assigned_user_id, next_action_at, duplicate_of_lead_id, close_reason, created_at, updated_at,"
					+ " version)"
					+ " VALUES (?, ?, 'ASSIGNED', 'LONG_TERM_RENT', 'WEB', 'Chủ nhà', ?, 'chu.nha@example.vn', '48',"
					+ " 'Đà Nẵng', '20242', 'Hải Châu', ?, ?, 16.067800, 108.220800, ?, 15000000, 'MONTH',"
					+ " DATE '2026-12-01', 'Có nội thất', 'Buổi tối', ?, ?, ?,"
					+ " TIMESTAMP WITH TIME ZONE '2026-10-20 09:00:00+07', ?, 'Gọi lại sau',"
					+ " TIMESTAMP WITH TIME ZONE '2026-10-18 08:00:00+07', TIMESTAMP WITH TIME ZONE '2026-10-19 10:30:00+07',"
					+ " ?)",
					leadId, String.format("SV-MIGR-%04d", n), String.format("+8490000%04d", n), n + " Bạch Đằng",
					n + " bạch đằng", oldType, consentId,
					"{\"propertyType\":" + (oldType == null ? "null" : "\"" + oldType + "\"") + "}",
					ASSIGNEE, n == 1 ? null : FIRST_LEAD, n);
			jdbc.update("INSERT INTO owner_lead_activities (id, lead_id, type, actor_user_id, from_status, to_status,"
					+ " from_assignee_id, to_assignee_id, note, next_action_at, occurred_at)"
					+ " VALUES (?, ?, 'ASSIGNED', ?, 'NEW', 'ASSIGNED', NULL, ?, 'Giao cho Sale', now(), now())",
					n, leadId, ASSIGNEE, ASSIGNEE);
			jdbc.update("INSERT INTO owner_lead_access_logs (id, lead_id, user_id, accessed_at) VALUES (?, ?, ?, now())",
					n, leadId, ASSIGNEE);
			if (n > 1) {
				jdbc.update("INSERT INTO owner_lead_duplicate_candidates (lead_id, candidate_lead_id, detected_at)"
						+ " VALUES (?, ?, now())", leadId, FIRST_LEAD);
			}
			oldTypeById.put(leadId, oldType);
		}
		assertEquals(MAPPING.size(), count("owner_lead_activities"));
		assertEquals(MAPPING.size() - 1, count("owner_lead_duplicate_candidates"));
		leadsBefore = leads();
		otherTablesBefore = otherTables();

		flywayToLatest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.target(VERSION).load();
		flywayToLatest.migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	/** acquisition-owner-intake Q-16: each old value gets its new type, or none. */
	@Test
	void migrate_everyOldValue_getsItsNewTypeOrNull() {
		for (Map<String, Object> lead : leads()) {
			String oldType = oldTypeById.get((String) lead.get("id"));
			assertEquals(MAPPING.get(oldType), lead.get("property_type"), "lead stored with " + oldType);
		}
	}

	/** Only property_type changes: the other columns, the snapshot of the original and the row count stay. */
	@Test
	void migrate_changesNoOtherColumnAndNoRowCount() {
		List<Map<String, Object>> after = leads();

		assertEquals(leadsBefore.size(), after.size());
		assertEquals(withoutType(leadsBefore), withoutType(after));
		assertEquals(otherTablesBefore, otherTables());
	}

	/** The constraint now refuses an old value. */
	@Test
	void migrate_checkConstraint_refusesAnOldValue() {
		DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE owner_leads SET property_type = 'TWO_BEDROOM' WHERE id = ?",
						leadsBefore.get(0).get("id")));
		assertTrue(thrown.getMessage().contains("chk_owner_leads_property_type"), thrown.getMessage());
	}

	/** The script is safe to run twice: a second run changes nothing. */
	@Test
	void script_runAgain_changesNothing() throws IOException {
		List<Map<String, Object>> before = leads();

		jdbc.execute(script());

		assertEquals(before, leads());
		assertEquals(otherTablesBefore, otherTables());
		assertEquals(0, flywayToLatest.info().pending().length);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private List<Map<String, Object>> leads() {
		return jdbc.queryForList("SELECT * FROM owner_leads ORDER BY id");
	}

	private Map<String, List<Map<String, Object>>> otherTables() {
		Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
		for (String table : OTHER_TABLES) {
			rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
		}
		return rows;
	}

	private static List<Map<String, Object>> withoutType(List<Map<String, Object>> rows) {
		List<Map<String, Object>> copies = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			Map<String, Object> copy = new LinkedHashMap<>(row);
			copy.remove("property_type");
			copies.add(copy);
		}
		return copies;
	}

	private static String script() throws IOException {
		return new ClassPathResource(SCRIPT).getContentAsString(StandardCharsets.UTF_8);
	}
}
