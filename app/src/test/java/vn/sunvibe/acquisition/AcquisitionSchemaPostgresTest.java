package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;

/**
 * The acquisition tables built by Flyway on a real PostgreSQL (acquisition-owner-intake spec 4.2, lead-queue spec 4):
 * names, column types, the CHECK constraints of owner_leads, no foreign key out of the module, and the indexes.
 * Needs Docker, like {@code FlywayPostgresMigrationTest}; it is not skipped without it.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AcquisitionSchemaPostgresTest {

	private static final List<String> ACQUISITION_TABLES = List.of(
			"consent_records", "owner_leads", "owner_lead_duplicate_candidates", "owner_lead_activities",
			"owner_lead_access_logs", "owner_intake_idempotency_keys", "sale_territories", "sale_territory_changes");

	/** The 12 values of doc 09 (rebuild/09-lifecycle-and-rules.md, OwnerLead). */
	private static final List<String> LEAD_STATUSES = List.of(
			"NEW", "VALIDATING", "ASSIGNED", "CONTACTED", "SURVEY_SCHEDULED", "QUALIFIED", "AGREEMENT_PENDING",
			"ONBOARDED", "DUPLICATE", "REJECTED", "WITHDRAWN", "ON_HOLD");

	@Autowired
	private JdbcTemplate jdbc;

	private int sequence;

	@BeforeEach
	@AfterEach
	void cleanAcquisitionTables() {
		jdbc.update("DELETE FROM owner_intake_idempotency_keys");
		jdbc.update("DELETE FROM owner_lead_duplicate_candidates");
		jdbc.update("DELETE FROM owner_lead_activities");
		jdbc.update("DELETE FROM owner_lead_access_logs");
		jdbc.update("DELETE FROM owner_leads");
		jdbc.update("DELETE FROM consent_records");
	}

	@Test
	void schema_afterMigrate_hasAllAcquisitionTables() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

		for (String table : ACQUISITION_TABLES) {
			assertTrue(tables.contains(table), () -> "missing table " + table);
		}
	}

	@Test
	void schema_ownerLeads_hasTheDocumentedColumnsAndTypes() {
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("id", "character varying(26) NOT NULL");
		expected.put("request_code", "character varying(12) NOT NULL");
		expected.put("status", "character varying(30) NOT NULL");
		expected.put("intent", "character varying(20) NOT NULL");
		expected.put("source", "character varying(20) NOT NULL");
		expected.put("contact_name", "character varying(100) NOT NULL");
		expected.put("phone_normalized", "character varying(16) NOT NULL");
		expected.put("email", "character varying(254) NULL");
		expected.put("province_code", "character varying(10) NOT NULL");
		expected.put("province_name", "character varying(100) NOT NULL");
		expected.put("ward_code", "character varying(10) NOT NULL");
		expected.put("ward_name", "character varying(100) NOT NULL");
		expected.put("address_detail", "character varying(255) NOT NULL");
		expected.put("address_key", "character varying(255) NOT NULL");
		expected.put("latitude", "numeric(9,6) NULL");
		expected.put("longitude", "numeric(9,6) NULL");
		expected.put("property_type", "character varying(20) NULL");
		expected.put("asking_price_amount", "numeric(15,0) NULL");
		expected.put("asking_price_unit", "character varying(10) NULL");
		expected.put("available_from", "date NULL");
		expected.put("notes", "character varying(1000) NULL");
		expected.put("preferred_callback_time", "character varying(100) NULL");
		expected.put("consent_record_id", "character varying(26) NOT NULL");
		expected.put("submission_snapshot", "text NOT NULL");
		expected.put("assigned_user_id", "character varying(26) NULL");
		expected.put("next_action_at", "timestamp with time zone NULL");
		expected.put("duplicate_of_lead_id", "character varying(26) NULL");
		expected.put("close_reason", "character varying(500) NULL");
		expected.put("created_at", "timestamp with time zone NOT NULL");
		expected.put("updated_at", "timestamp with time zone NOT NULL");
		expected.put("version", "bigint NOT NULL");

		assertEquals(expected, columnsOf("owner_leads"));
		assertEquals("0", jdbc.queryForObject("SELECT column_default FROM information_schema.columns"
				+ " WHERE table_schema = 'public' AND table_name = 'owner_leads' AND column_name = 'version'",
				String.class));
	}

	@Test
	void schema_otherAcquisitionTables_haveTheDocumentedColumnsAndTypes() {
		assertEquals(Map.of(
				"id", "character varying(26) NOT NULL",
				"notice_version", "character varying(40) NOT NULL",
				"purpose", "character varying(40) NOT NULL",
				"channel", "character varying(20) NOT NULL",
				"ip_hash", "character varying(64) NULL",
				"user_agent_hash", "character varying(64) NULL",
				"accepted_at", "timestamp with time zone NOT NULL"), columnsOf("consent_records"));
		assertEquals(Map.of(
				"lead_id", "character varying(26) NOT NULL",
				"candidate_lead_id", "character varying(26) NOT NULL",
				"detected_at", "timestamp with time zone NOT NULL"), columnsOf("owner_lead_duplicate_candidates"));
		assertEquals(Map.of(
				"idempotency_key", "character varying(64) NOT NULL",
				"request_hash", "character(64) NOT NULL",
				"lead_id", "character varying(26) NOT NULL",
				"created_at", "timestamp with time zone NOT NULL"), columnsOf("owner_intake_idempotency_keys"));
		assertEquals(Map.of(
				"id", "bigint NOT NULL",
				"lead_id", "character varying(26) NOT NULL",
				"user_id", "character varying(26) NOT NULL",
				"accessed_at", "timestamp with time zone NOT NULL"), columnsOf("owner_lead_access_logs"));
		assertEquals(Map.of(
				"ward_code", "character varying(10) NOT NULL",
				"user_id", "character varying(26) NOT NULL",
				"assigned_by", "character varying(26) NOT NULL",
				"assigned_at", "timestamp with time zone NOT NULL"), columnsOf("sale_territories"));
		assertEquals(Map.of(
				"id", "bigint NOT NULL",
				"ward_code", "character varying(10) NOT NULL",
				"old_user_id", "character varying(26) NULL",
				"new_user_id", "character varying(26) NULL",
				"actor_user_id", "character varying(26) NOT NULL",
				"occurred_at", "timestamp with time zone NOT NULL"), columnsOf("sale_territory_changes"));

		Map<String, String> activityColumns = new LinkedHashMap<>();
		activityColumns.put("id", "bigint NOT NULL");
		activityColumns.put("lead_id", "character varying(26) NOT NULL");
		activityColumns.put("type", "character varying(30) NOT NULL");
		activityColumns.put("actor_user_id", "character varying(26) NULL");
		activityColumns.put("from_status", "character varying(30) NULL");
		activityColumns.put("to_status", "character varying(30) NULL");
		activityColumns.put("from_assignee_id", "character varying(26) NULL");
		activityColumns.put("to_assignee_id", "character varying(26) NULL");
		activityColumns.put("note", "character varying(2000) NULL");
		activityColumns.put("next_action_at", "timestamp with time zone NULL");
		activityColumns.put("occurred_at", "timestamp with time zone NOT NULL");
		assertEquals(activityColumns, columnsOf("owner_lead_activities"));
	}

	@Test
	void schema_ownerLeads_rejectsInvalidRows() {
		String consentId = insertConsent();

		assertRejected(leadInsert(consentId, "status", "'UNKNOWN'"), "chk_owner_leads_status");
		assertRejected(leadInsert(consentId, "intent", "'RENT'"), "chk_owner_leads_intent");
		assertRejected(leadInsert(consentId, "source", "'FAX'"), "chk_owner_leads_source");
		assertRejected(leadInsert(consentId, "property_type", "'CASTLE'"), "chk_owner_leads_property_type");
		assertRejected(leadInsert(consentId, "asking_price_unit", "'YEAR'", "asking_price_amount", "1000000"),
				"chk_owner_leads_price_unit");
		assertRejected(leadInsert(consentId, "latitude", "16.047079"), "chk_owner_leads_geo_pair");
		assertRejected(leadInsert(consentId, "longitude", "108.206230"), "chk_owner_leads_geo_pair");
		assertRejected(leadInsert(consentId, "asking_price_amount", "1000000"), "chk_owner_leads_price_pair");
		assertRejected(leadInsert(consentId, "asking_price_unit", "'MONTH'"), "chk_owner_leads_price_pair");
		assertRejected(leadInsert(consentId, "asking_price_amount", "0", "asking_price_unit", "'MONTH'"),
				"chk_owner_leads_price_positive");
		assertRejected(leadInsert(consentId, "asking_price_amount", "-5", "asking_price_unit", "'MONTH'"),
				"chk_owner_leads_price_positive");
		assertRejected(leadInsert("01MISSINGCONSENT0000000000", "id", "'01LEADMISSINGCONSENT00000'"),
				"fk_owner_leads_consent_record_id");

		jdbc.update(leadInsert(consentId, "request_code", "'SV-AAAA-0001'"));
		assertRejected(leadInsert(consentId, "request_code", "'SV-AAAA-0001'", "id", "'01LEADDUPLICATECODE0000'"),
				"uk_owner_leads_request_code");
	}

	@Test
	void schema_ownerLeads_acceptsAValidRowWithGeoAndPrice() {
		String consentId = insertConsent();

		jdbc.update(leadInsert(consentId, "latitude", "16.047079", "longitude", "108.206230",
				"asking_price_amount", "12000000", "asking_price_unit", "'MONTH'", "property_type", "'APARTMENT'"));

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM owner_leads", Integer.class));
	}

	@Test
	void schema_ownerLeads_acceptsAllTwelveStatuses() {
		String consentId = insertConsent();

		for (String status : LEAD_STATUSES) {
			jdbc.update(leadInsert(consentId, "status", "'" + status + "'"));
		}

		assertEquals(12, jdbc.queryForObject("SELECT count(*) FROM owner_leads", Integer.class));
	}

	@Test
	void schema_ownerLeads_acceptsEveryIntentSourcePropertyTypeAndPriceUnit() {
		String consentId = insertConsent();

		for (String intent : List.of("SHORT_TERM_RENT", "LONG_TERM_RENT", "SALE")) {
			jdbc.update(leadInsert(consentId, "intent", "'" + intent + "'"));
		}
		for (String source : List.of("WEB", "REFERRAL", "OUTBOUND", "PARTNER", "STAFF_ENTRY")) {
			jdbc.update(leadInsert(consentId, "source", "'" + source + "'"));
		}
		for (OwnerLeadPropertyType type : OwnerLeadPropertyType.values()) {
			jdbc.update(leadInsert(consentId, "property_type", "'" + type.name() + "'"));
		}
		jdbc.update(leadInsert(consentId, "property_type", "NULL"));
		for (String unit : List.of("NIGHT", "MONTH", "TOTAL")) {
			jdbc.update(leadInsert(consentId, "asking_price_amount", "1", "asking_price_unit", "'" + unit + "'"));
		}

		assertEquals(3 + 5 + OwnerLeadPropertyType.values().length + 1 + 3,
				jdbc.queryForObject("SELECT count(*) FROM owner_leads", Integer.class));
	}

	/**
	 * acquisition-owner-intake BR-3, Q-16: the constraint holds exactly the six types of the enum; the old values
	 * (unit layouts, townhouse, land, "other") are refused like any unknown value.
	 */
	@Test
	void schema_ownerLeads_propertyTypeCheck_isExactlyTheEnumAndRefusesTheOldValues() {
		String consentId = insertConsent();

		for (String old : List.of("STUDIO", "ONE_BEDROOM", "TWO_BEDROOM", "THREE_BEDROOM", "FOUR_BEDROOM", "DUPLEX",
				"PENTHOUSE", "TOWNHOUSE", "LAND", "OTHER", "CASTLE")) {
			assertRejected(leadInsert(consentId, "property_type", "'" + old + "'"), "chk_owner_leads_property_type");
		}
		String definition = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
				+ "WHERE conname = 'chk_owner_leads_property_type'", String.class);
		List<String> allowed = Pattern.compile("'([A-Z_]+)'").matcher(definition).results()
				.map(match -> match.group(1)).toList();
		assertEquals(Arrays.stream(OwnerLeadPropertyType.values()).map(Enum::name).toList(), allowed, definition);
	}

	@Test
	void schema_noForeignKeyLeavesTheModule() {
		List<String> foreignKeys = jdbc.queryForList("""
				SELECT tc.table_name || ' -> ' || ccu.table_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
				WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
				""", String.class);

		List<String> leaving = foreignKeys.stream()
				.filter(fk -> ACQUISITION_TABLES.contains(fk.substring(0, fk.indexOf(" ->"))))
				.filter(fk -> !ACQUISITION_TABLES.contains(fk.substring(fk.indexOf("-> ") + 3)))
				.toList();

		assertTrue(foreignKeys.stream().anyMatch(fk -> fk.startsWith("owner_leads -> ")),
				"owner_leads has no foreign key at all: " + foreignKeys);
		assertEquals(List.of(), leaving, "a foreign key leaves the acquisition module");
	}

	@Test
	void schema_foreignKeysAndChecks_haveTheDocumentedNames() {
		List<String> constraints = jdbc.queryForList(
				"SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = 'public'",
				String.class);

		for (String name : List.of("owner_leads_pkey", "uk_owner_leads_request_code", "fk_owner_leads_consent_record_id",
				"fk_owner_leads_duplicate_of_lead_id", "chk_owner_leads_status", "chk_owner_leads_intent",
				"chk_owner_leads_source", "chk_owner_leads_property_type", "chk_owner_leads_price_unit",
				"chk_owner_leads_geo_pair", "chk_owner_leads_price_pair", "chk_owner_leads_price_positive")) {
			assertTrue(constraints.contains(name), () -> "missing constraint " + name);
		}
	}

	@Test
	void schema_indexes_exist() {
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

		for (String name : List.of("idx_owner_leads_phone_created_at", "idx_owner_leads_ward_code",
				"idx_owner_leads_assigned", "idx_owner_leads_status_created_at", "idx_owner_leads_consent_record_id",
				"idx_owner_leads_duplicate_of")) {
			assertTrue(indexes.contains(name), () -> "missing index " + name);
		}
		assertEquals(List.of("phone_normalized", "created_at"), indexColumns("idx_owner_leads_phone_created_at"));
		assertEquals(List.of("assigned_user_id", "status", "created_at"), indexColumns("idx_owner_leads_assigned"));
		assertEquals(List.of("status", "created_at"), indexColumns("idx_owner_leads_status_created_at"));
	}

	@Test
	void schema_everyForeignKeyColumnOfTheModule_hasAnIndex() {
		String tableList = ACQUISITION_TABLES.stream().map(t -> "'" + t + "'").collect(Collectors.joining(", "));
		List<String> unindexed = jdbc.queryForList("""
				SELECT tc.table_name || '.' || kcu.column_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name
				WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
				  AND tc.table_name IN (@TABLES@)
				  AND NOT EXISTS (
				    SELECT 1 FROM pg_indexes i
				    WHERE i.schemaname = 'public' AND i.tablename = tc.table_name
				      AND (i.indexdef LIKE '%(' || kcu.column_name || ')%'
				           OR i.indexdef LIKE '%(' || kcu.column_name || ',%')
				  )
				""".replace("@TABLES@", tableList), String.class);

		assertEquals(List.of(), unindexed, "foreign key columns without an index starting at them");
	}

	@Test
	void schema_secondaryTables_haveTheDocumentedIndexes() {
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

		assertEquals(List.of("lead_id", "occurred_at"), indexColumns("idx_owner_lead_activities_lead_id_occurred_at"));
		assertEquals(List.of("lead_id", "accessed_at"), indexColumns("idx_owner_lead_access_logs_lead_id_accessed_at"));
		assertEquals(List.of("user_id"), indexColumns("idx_owner_lead_access_logs_user_id"));
		assertEquals(List.of("candidate_lead_id"), indexColumns("idx_owner_lead_duplicate_candidates_candidate"));
		assertEquals(List.of("lead_id"), indexColumns("idx_owner_intake_idempotency_keys_lead_id"));
		assertEquals(List.of("user_id"), indexColumns("idx_sale_territories_user_id"));
		assertEquals(List.of("ward_code"), indexColumns("idx_sale_territory_changes_ward_code"));
		assertTrue(indexes.contains("owner_lead_duplicate_candidates_pkey"));
	}

	/** column_name -> "type(length) NULL|NOT NULL" for every column of the table, in ordinal order. */
	private Map<String, String> columnsOf(String table) {
		return jdbc.query("""
				SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable
				FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = ?
				ORDER BY ordinal_position
				""", rs -> {
			Map<String, String> columns = new LinkedHashMap<>();
			while (rs.next()) {
				String type = rs.getString("data_type");
				if (rs.getObject("character_maximum_length") != null) {
					type += "(" + rs.getInt("character_maximum_length") + ")";
				}
				else if ("numeric".equals(type)) {
					type += "(" + rs.getInt("numeric_precision") + "," + rs.getInt("numeric_scale") + ")";
				}
				columns.put(rs.getString("column_name"),
						type + ("NO".equals(rs.getString("is_nullable")) ? " NOT NULL" : " NULL"));
			}
			return columns;
		}, table);
	}

	private List<String> indexColumns(String indexName) {
		return jdbc.queryForList("""
				SELECT a.attname
				FROM pg_index ix
				JOIN pg_class c ON c.oid = ix.indexrelid
				JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS k(attnum, ord) ON true
				JOIN pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = k.attnum
				WHERE c.relname = ?
				ORDER BY k.ord
				""", String.class, indexName);
	}

	private String insertConsent() {
		String id = String.format("01CONSENT%017d", ++sequence);
		jdbc.update("INSERT INTO consent_records (id, notice_version, purpose, channel, accepted_at)"
				+ " VALUES (?, 'v1', 'OWNER_INTAKE_CONTACT', 'WEB', now())", id);
		return id;
	}

	/**
	 * A valid owner_leads INSERT whose listed columns are overridden. {@code overrides} are column, SQL literal
	 * pairs; the id and request code are unique per call unless overridden.
	 */
	private String leadInsert(String consentId, String... overrides) {
		Map<String, String> values = new LinkedHashMap<>();
		int n = ++sequence;
		values.put("id", String.format("'01LEAD%020d'", n));
		values.put("request_code", String.format("'SV-T%03d-%04d'", n / 10000, n % 10000));
		values.put("status", "'NEW'");
		values.put("intent", "'LONG_TERM_RENT'");
		values.put("source", "'WEB'");
		values.put("contact_name", "'Test Owner'");
		values.put("phone_normalized", "'+84901234567'");
		values.put("province_code", "'48'");
		values.put("province_name", "'Da Nang'");
		values.put("ward_code", "'20242'");
		values.put("ward_name", "'Hai Chau'");
		values.put("address_detail", "'1 Example Street'");
		values.put("address_key", "'1 example street'");
		values.put("consent_record_id", "'" + consentId + "'");
		values.put("submission_snapshot", "'{}'");
		values.put("created_at", "now()");
		values.put("updated_at", "now()");
		for (int i = 0; i < overrides.length; i += 2) {
			values.put(overrides[i], overrides[i + 1]);
		}
		return "INSERT INTO owner_leads (" + String.join(", ", values.keySet()) + ") VALUES ("
				+ values.values().stream().collect(Collectors.joining(", ")) + ")";
	}

	/** The insert must fail on exactly the named constraint, not on a typo of the test row. */
	private void assertRejected(String sql, String constraint) {
		DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update(sql));
		assertTrue(thrown.getMessage().contains(constraint), "expected constraint " + constraint + ": " + thrown.getMessage());
	}
}
