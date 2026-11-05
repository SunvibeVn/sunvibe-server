package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
 * The intake tables (property-create intake spec 5.2 files 5 to 12, I-1, I-2, I-3, 4.10) on a real PostgreSQL. Every
 * refusal asserts the constraint or index by name, except the NOT NULL test, which asserts the column. Needs Docker, like
 * {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryIntakeSchemaPostgresTest {

	private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 6, 9, 0, 0, 0, ZoneOffset.UTC);
	private static final String HASH = "a".repeat(64);
	private static final String OTHER_HASH = "b".repeat(64);
	private static final String USER = "01SCHUSER0000000000000000A";
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String CHECK_VIOLATION = "23514";
	private static final String NOT_NULL_VIOLATION = "23502";

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(1000);
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

	// ==================== intakes (spec 5.2 file 5, I-1, I-2) ====================

	/** I-1: one open intake per lead and cycle. */
	@Test
	void schema_twoOpenIntakes_refused() {
		String lead = id("LEAD");
		insert("inventory_intakes", intake(lead, 1, "DRAFT"));

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_intakes_open_per_cycle",
				() -> insert("inventory_intakes", intake(lead, 2, "OWNER_CONFIRMED")));
	}

	@Test
	void schema_abandonedPlusOpen_allowed() {
		String lead = id("LEAD");
		insert("inventory_intakes", intake(lead, 1, "ABANDONED"));

		insert("inventory_intakes", intake(lead, 2, "DRAFT"));

		assertEquals(2, count("inventory_intakes WHERE lead_id = '" + lead + "'"));
	}

	/** I-2: one completed intake per source revision. */
	@Test
	void schema_twoCompletedOneSource_refused() {
		String lead = id("LEAD");
		Map<String, Object> first = completed(lead, 1);
		Map<String, Object> second = completed(lead, 2);
		second.put("source_checklist_id", first.get("source_checklist_id"));
		insertCompleted(first);

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_intakes_completed_source", () -> insertCompleted(second));
	}

	@Test
	void schema_intakeNoPositive_refused() {
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_intake_no",
				() -> insert("inventory_intakes", intake(id("LEAD"), 0, "DRAFT")));
	}

	@Test
	void schema_intakeNoRepeatedInALead_refused() {
		String lead = id("LEAD");
		insert("inventory_intakes", intake(lead, 1, "ABANDONED"));

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_intakes_lead_intake_no",
				() -> insert("inventory_intakes", intake(lead, 1, "ABANDONED")));
	}

	/** The five IS JSON columns refuse text and the wrong JSON shape. */
	@Test
	void schema_jsonColumns_refused() {
		Map<String, String> arrayChecks = Map.of("selection", "chk_inventory_intakes_selection",
				"availability", "chk_inventory_intakes_availability");
		arrayChecks.forEach((column, check) -> {
			for (String bad : List.of("not json", "{\"a\": 1}")) {
				Map<String, Object> row = intake(id("LEAD"), 1, "DRAFT");
				row.put(column, bad);

				assertRefusedBy(CHECK_VIOLATION, check, () -> insert("inventory_intakes", row));
			}
		});

		Map<String, Object> mappings = intake(id("LEAD"), 1, "DRAFT");
		mappings.put("target_mode", "LINK");
		mappings.put("target_mappings", "{\"a\": 1}");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_target_mappings",
				() -> insert("inventory_intakes", mappings));

		Map<String, Object> versions = intake(id("LEAD"), 1, "DRAFT");
		versions.put("used_versions", "[1]");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_used_versions",
				() -> insert("inventory_intakes", versions));

		String intake = insertDraft();
		Map<String, Object> request = request(intake);
		request.put("display_snapshot", "[1]");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_owner_requests_display_snapshot",
				() -> insert("inventory_owner_requests", request));
	}

	@Test
	void schema_completedWithoutResult_refused() {
		Map<String, Object> row = completed(id("LEAD"), 1);
		row.remove("result_property_id");

		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_completed_result", () -> insertCompleted(row));
	}

	@Test
	void schema_completedWithoutCompletedAt_refused() {
		Map<String, Object> row = completed(id("LEAD"), 1);
		row.remove("completed_at");

		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_completed_result", () -> insertCompleted(row));
	}

	@Test
	void schema_completedWithoutProof_refused() {
		String draft = insertDraft();

		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_completed_proof", () -> jdbc.update(
				"UPDATE inventory_intakes SET status = 'COMPLETED', result_property_id = 'P', completed_at = ?"
						+ " WHERE id = ?", T0, draft));
	}

	@Test
	void schema_mappingsOnlyWithLink() {
		Map<String, Object> create = intake(id("LEAD"), 1, "DRAFT");
		create.put("target_mode", "CREATE");
		create.put("target_mappings", "[]");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_mappings_only_link",
				() -> insert("inventory_intakes", create));

		Map<String, Object> none = intake(id("LEAD"), 1, "DRAFT");
		none.put("target_mappings", "[]");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_mappings_only_link",
				() -> insert("inventory_intakes", none));

		Map<String, Object> link = intake(id("LEAD"), 1, "DRAFT");
		link.put("target_mode", "LINK");
		link.put("target_mappings", "[]");
		insert("inventory_intakes", link);
	}

	// ==================== requests and proofs (spec 5.2 files 6, 7, I-3) ====================

	@Test
	void schema_twoOpenRequests_refused() {
		String intake = insertDraft();
		insert("inventory_owner_requests", request(intake));

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_owner_requests_open",
				() -> insert("inventory_owner_requests", request(intake)));
	}

	@Test
	void schema_voidedPlusOpenRequest_allowed() {
		String intake = insertDraft();
		Map<String, Object> voided = request(intake);
		voided.put("voided_at", T0);
		insert("inventory_owner_requests", voided);

		insert("inventory_owner_requests", request(intake));

		assertEquals(2, count("inventory_owner_requests WHERE intake_id = '" + intake + "'"));
	}

	/** I-3: one proof neither withdrawn nor voided per intake. */
	@Test
	void schema_twoLiveProofs_refused() {
		String intake = insertDraft();
		insert("inventory_owner_proofs", proof(intake, insertConsumedRequest(intake)));

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_owner_proofs_in_force",
				() -> insert("inventory_owner_proofs", proof(intake, insertConsumedRequest(intake))));
	}

	@Test
	void schema_withdrawnPlusLiveProof_allowed() {
		String intake = insertDraft();
		Map<String, Object> withdrawn = proof(intake, insertConsumedRequest(intake));
		withdrawn.put("withdrawn_at", T0);
		insert("inventory_owner_proofs", withdrawn);

		insert("inventory_owner_proofs", proof(intake, insertConsumedRequest(intake)));

		assertEquals(2, count("inventory_owner_proofs WHERE intake_id = '" + intake + "'"));
	}

	@Test
	void schema_duplicateRequestProof_refused() {
		String intake = insertDraft();
		String request = insertConsumedRequest(intake);
		Map<String, Object> first = proof(intake, request);
		first.put("voided_at", T0);
		insert("inventory_owner_proofs", first);

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_owner_proofs_request_id",
				() -> insert("inventory_owner_proofs", proof(intake, request)));
	}

	// ==================== mappings (spec 5.2 file 8) ====================

	@Test
	void schema_twoCanonicalMappings_refused() {
		String intake = insertDraft();
		insert("inventory_unit_mappings", mapping(intake, true));

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_unit_mappings_canonical",
				() -> insert("inventory_unit_mappings", mapping(intake, true)));
	}

	@Test
	void schema_oneUnitForTwoSpaces_refused() {
		String intake = insertDraft();
		Map<String, Object> first = mapping(intake, false);
		Map<String, Object> second = mapping(intake, false);
		second.put("unit_id", first.get("unit_id"));
		insert("inventory_unit_mappings", first);

		assertRefusedBy(UNIQUE_VIOLATION, "uq_inventory_unit_mappings_intake_unit",
				() -> insert("inventory_unit_mappings", second));
	}

	// ==================== cases (spec 5.2 file 9, 4.10) ====================

	@Test
	void schema_caseTwoCandidates_refused() {
		String intake = insertDraft();
		String other = insertDraft();
		Map<String, Object> both = propertyCase(intake);
		both.put("candidate_intake_id", other);
		both.put("candidate_scope_hash", OTHER_HASH);
		both.put("signal", "INTAKE");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_one_candidate",
				() -> insert("inventory_duplicate_cases", both));

		Map<String, Object> neither = propertyCase(intake);
		neither.remove("candidate_property_id");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_one_candidate",
				() -> insert("inventory_duplicate_cases", neither));
	}

	/** spec 4.10: the insert names the predicate of the partial index, spelled as in the index. */
	@Test
	void schema_pairKeyConflict_doNothing() {
		Map<String, Object> first = propertyCase(insertDraft());
		insert("inventory_duplicate_cases", first);
		Map<String, Object> second = propertyCase(insertDraft());
		second.put("pair_key", first.get("pair_key"));

		int inserted = jdbc.update("INSERT INTO inventory_duplicate_cases (" + String.join(", ", second.keySet())
				+ ") VALUES (" + String.join(", ", Collections.nCopies(second.size(), "?")) + ")"
				+ " ON CONFLICT (pair_key) WHERE status <> 'CLOSED' DO NOTHING", second.values().toArray());

		assertEquals(0, inserted);
		assertEquals(1, count("inventory_duplicate_cases WHERE pair_key = '" + first.get("pair_key") + "'"));
	}

	@Test
	void schema_closedPairAllowsNewRow() {
		Map<String, Object> closed = propertyCase(insertDraft());
		closed.put("status", "CLOSED");
		Map<String, Object> closedAgain = propertyCase(insertDraft());
		closedAgain.put("status", "CLOSED");
		closedAgain.put("pair_key", closed.get("pair_key"));
		Map<String, Object> open = propertyCase(insertDraft());
		open.put("pair_key", closed.get("pair_key"));
		insert("inventory_duplicate_cases", closed);
		insert("inventory_duplicate_cases", closedAgain);

		insert("inventory_duplicate_cases", open);

		assertEquals(3, count("inventory_duplicate_cases WHERE pair_key = '" + closed.get("pair_key") + "'"));
	}

	@Test
	void schema_decidedNeedsOutcomeAndTime() {
		Map<String, Object> noTime = propertyCase(insertDraft());
		noTime.put("status", "DECIDED");
		noTime.put("outcome", "SAME_ASSET");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_decided_at",
				() -> insert("inventory_duplicate_cases", noTime));

		Map<String, Object> decided = propertyCase(insertDraft());
		decided.put("status", "DECIDED");
		decided.put("outcome", "DIFFERENT_ASSET");
		decided.put("decided_at", T0);
		insert("inventory_duplicate_cases", decided);
	}

	@Test
	void schema_decidedExactlyWithOutcome() {
		Map<String, Object> noOutcome = propertyCase(insertDraft());
		noOutcome.put("status", "DECIDED");
		noOutcome.put("decided_at", T0);
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_decided_outcome",
				() -> insert("inventory_duplicate_cases", noOutcome));

		Map<String, Object> openWithOutcome = propertyCase(insertDraft());
		openWithOutcome.put("outcome", "SAME_ASSET");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_decided_outcome",
				() -> insert("inventory_duplicate_cases", openWithOutcome));
	}

	@Test
	void schema_intakeSignalNeedsIntakeCandidate() {
		Map<String, Object> onProperty = propertyCase(insertDraft());
		onProperty.put("signal", "INTAKE");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_intake_signal",
				() -> insert("inventory_duplicate_cases", onProperty));

		Map<String, Object> onIntake = intakeCase(insertDraft(), insertDraft());
		onIntake.put("signal", "ADDRESS");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_intake_signal",
				() -> insert("inventory_duplicate_cases", onIntake));
	}

	@Test
	void schema_candidateScopeNeedsIntake_refused() {
		Map<String, Object> onProperty = propertyCase(insertDraft());
		onProperty.put("candidate_scope_hash", OTHER_HASH);
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_candidate_scope",
				() -> insert("inventory_duplicate_cases", onProperty));

		Map<String, Object> noScope = intakeCase(insertDraft(), insertDraft());
		noScope.remove("candidate_scope_hash");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_candidate_scope",
				() -> insert("inventory_duplicate_cases", noScope));
	}

	@Test
	void schema_nullStatusOrPairKey_refused() {
		Map<String, Object> noStatus = propertyCase(insertDraft());
		noStatus.remove("status");
		assertEquals("status", notNullColumnOf(() -> insert("inventory_duplicate_cases", noStatus)));

		Map<String, Object> noKey = propertyCase(insertDraft());
		noKey.remove("pair_key");
		assertEquals("pair_key", notNullColumnOf(() -> insert("inventory_duplicate_cases", noKey)));

		Map<String, Object> noIntakeStatus = intake(id("LEAD"), 1, "DRAFT");
		noIntakeStatus.remove("status");
		assertEquals("status", notNullColumnOf(() -> insert("inventory_intakes", noIntakeStatus)));
	}

	@Test
	void schema_badStatusValues_refused() {
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_status",
				() -> insert("inventory_intakes", intake(id("LEAD"), 1, "OPEN")));

		Map<String, Object> mode = intake(id("LEAD"), 1, "DRAFT");
		mode.put("target_mode", "MERGE");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_intakes_target_mode", () -> insert("inventory_intakes", mode));

		String draft = insertDraft();
		Map<String, Object> ownerCheck = proof(draft, insertConsumedRequest(draft));
		ownerCheck.put("owner_check", "SKIPPED");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_owner_proofs_owner_check",
				() -> insert("inventory_owner_proofs", ownerCheck));

		Map<String, Object> caseStatus = propertyCase(insertDraft());
		caseStatus.put("status", "REOPENED");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_status",
				() -> insert("inventory_duplicate_cases", caseStatus));

		Map<String, Object> signal = propertyCase(insertDraft());
		signal.put("signal", "PHONE");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_signal",
				() -> insert("inventory_duplicate_cases", signal));

		Map<String, Object> outcome = propertyCase(insertDraft());
		outcome.put("status", "DECIDED");
		outcome.put("decided_at", T0);
		outcome.put("outcome", "MAYBE");
		assertRefusedBy(CHECK_VIOLATION, "chk_inventory_duplicate_cases_outcome",
				() -> insert("inventory_duplicate_cases", outcome));
	}

	// ==================== suspensions, events, logs (spec 5.2 files 10 to 12) ====================

	/** D5-16: a data-rights request can come before any intake. */
	@Test
	void schema_suspensionWithoutIntake_allowed() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id("SUSP"));
		row.put("lead_id", id("LEAD"));
		row.put("reason", "Yêu cầu dữ liệu");
		row.put("created_by_user_id", USER);
		row.put("created_at", T0);

		insert("inventory_suspensions", row);

		assertEquals(1, count("inventory_suspensions WHERE id = '" + row.get("id") + "'"));
	}

	@Test
	void schema_eventAndLogIds_generated() {
		String intake = insertDraft();

		jdbc.update("INSERT INTO inventory_intake_events (intake_id, action, occurred_at) VALUES (?, 'SELECTION', ?)",
				intake, T0);
		jdbc.update("INSERT INTO inventory_access_logs (user_id, lead_id, action, accessed_at)"
				+ " VALUES (?, ?, 'BLOCK_READ', ?)", USER, id("LEAD"), T0);

		assertEquals(1, count("inventory_intake_events WHERE intake_id = '" + intake + "'"));
		assertEquals(1, count("inventory_access_logs WHERE user_id = '" + USER + "' AND action = 'BLOCK_READ'"));
	}

	// ==================== rows ====================

	private String insertDraft() {
		Map<String, Object> row = intake(id("LEAD"), 1, "DRAFT");
		insert("inventory_intakes", row);
		return row.get("id").toString();
	}

	private Map<String, Object> intake(String lead, int no, String status) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id("INTK"));
		row.put("intake_no", no);
		row.put("lead_id", lead);
		row.put("intent_cycle", 1);
		row.put("status", status);
		row.put("source_checklist_id", id("CHKL"));
		row.put("source_sha256", HASH);
		row.put("rental_scope", "WHOLE");
		row.put("asset_type", "HOUSE");
		row.put("ward_code", "20242");
		row.put("address_norm", "1 bach dang");
		row.put("selection", "[]");
		row.put("created_at", T0);
		row.put("updated_at", T0);
		return row;
	}

	private Map<String, Object> completed(String lead, int no) {
		Map<String, Object> row = intake(lead, no, "DRAFT");
		row.put("result_property_id", id("PROP"));
		row.put("completed_at", T0);
		return row;
	}

	/** A row can only reach COMPLETED with a proof, and a proof needs its intake first, so the status moves last. */
	private void insertCompleted(Map<String, Object> row) {
		Map<String, Object> draft = new LinkedHashMap<>(row);
		draft.put("status", "ABANDONED");
		insert("inventory_intakes", draft);
		String intake = row.get("id").toString();
		Map<String, Object> proof = proof(intake, insertConsumedRequest(intake));
		insert("inventory_owner_proofs", proof);
		jdbc.update("UPDATE inventory_intakes SET status = 'COMPLETED', proof_id = ? WHERE id = ?", proof.get("id"),
				intake);
	}

	private Map<String, Object> request(String intake) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id("RQST"));
		row.put("intake_id", intake);
		row.put("code_hash", HASH);
		row.put("expires_at", T0.plusDays(7));
		row.put("created_by_user_id", USER);
		row.put("created_at", T0);
		return row;
	}

	private String insertConsumedRequest(String intake) {
		Map<String, Object> row = request(intake);
		row.put("consumed_at", T0);
		insert("inventory_owner_requests", row);
		return row.get("id").toString();
	}

	private Map<String, Object> proof(String intake, String request) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id("PROF"));
		row.put("intake_id", intake);
		row.put("request_id", request);
		row.put("lead_id", id("LEAD"));
		row.put("intent_cycle", 1);
		row.put("checklist_id", id("CHKL"));
		row.put("user_id", USER);
		row.put("scope_hash", HASH);
		row.put("statement_version", "owner-link-2026-11");
		row.put("confirmed_at", T0);
		row.put("owner_check", "REQUIRED");
		row.put("sign_in_losses", 0);
		return row;
	}

	private Map<String, Object> mapping(String intake, boolean canonical) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("intake_id", intake);
		row.put("space_id", id("SPAC"));
		row.put("space_code", "P101");
		row.put("unit_id", id("UNIT"));
		row.put("canonical", canonical);
		return row;
	}

	private Map<String, Object> propertyCase(String intake) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id("CASE"));
		row.put("intake_id", intake);
		row.put("candidate_property_id", id("PROP"));
		row.put("pair_key", "P:" + id("PKEY"));
		row.put("scope_hash", HASH);
		row.put("signal", "ADDRESS");
		row.put("status", "OPEN");
		row.put("created_at", T0);
		return row;
	}

	private Map<String, Object> intakeCase(String intake, String candidate) {
		Map<String, Object> row = propertyCase(intake);
		row.remove("candidate_property_id");
		row.put("candidate_intake_id", candidate);
		row.put("candidate_scope_hash", OTHER_HASH);
		row.put("signal", "INTAKE");
		return row;
	}

	// ==================== helpers ====================

	private void insert(String table, Map<String, Object> row) {
		jdbc.update("INSERT INTO " + table + " (" + String.join(", ", row.keySet()) + ") VALUES ("
				+ String.join(", ", Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
	}

	private int count(String tableAndFilter) {
		return jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Integer.class);
	}

	/** The statement fails with this SQLSTATE on this constraint or unique index. */
	private static void assertRefusedBy(String sqlState, String constraint, Runnable statement) {
		SQLException failure = failureOf(statement);
		assertEquals(sqlState, failure.getSQLState(), failure.getMessage());
		assertTrue(failure.getMessage().contains("\"" + constraint + "\""), failure.getMessage());
	}

	/** The column PostgreSQL names in the message of a NOT NULL violation. */
	private static String notNullColumnOf(Runnable statement) {
		SQLException failure = failureOf(statement);
		assertEquals(NOT_NULL_VIOLATION, failure.getSQLState(), failure.getMessage());
		String message = failure.getMessage();
		int start = message.indexOf('"') + 1;
		return message.substring(start, message.indexOf('"', start));
	}

	private static SQLException failureOf(Runnable statement) {
		try {
			statement.run();
		} catch (DataAccessException e) {
			for (Throwable cause = e; cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException failure) {
					return failure;
				}
			}
			throw e;
		}
		return fail("the statement was accepted");
	}

	/** A fresh 26-character id with the marker. */
	private String id(String marker) {
		return String.format("01SCH%s%017d", marker, sequence.incrementAndGet());
	}
}
