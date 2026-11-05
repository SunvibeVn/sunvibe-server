package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
 * The requalification migrations (property-create requalification spec 5.2, I-1, S-1) on a real PostgreSQL holding rows
 * of the previous schema: the database is migrated to the last version before this slice, filled, then migrated to the
 * latest one. The partial index allows one unmarked PASSED per lead and cycle, every old row stays as it was and the
 * previous server's inserts are still accepted. Needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistRequalificationSchemaPostgresTest {

	/** The last version before this slice. */
	private static final String PREVIOUS_VERSION = "202611051011";

	private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 4, 9, 0, 0, 0, ZoneOffset.UTC);
	private static final String SALE = "01RQLSALE00000000000000001";
	private static final String MANAGER = "01RQLMGR000000000000000001";
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String CHECK_VIOLATION = "23514";
	private static final String FOREIGN_KEY_VIOLATION = "23503";
	/** The queries of scripts/ops/README.md section 7, as written there. */
	private static final String RUNBOOK_DUPLICATES = "SELECT lead_id, intent_cycle, count(*) FROM survey_checklists"
			+ " WHERE status = 'PASSED' GROUP BY 1, 2 HAVING count(*) > 1";
	private static final String RUNBOOK_LOCKS = "SELECT l.pid, l.mode, l.granted, a.state, now() - a.xact_start AS age"
			+ " FROM pg_locks l JOIN pg_stat_activity a USING (pid) WHERE l.relation = 'survey_checklists'::regclass"
			+ " AND l.pid <> pg_backend_pid()";
	private static final String RUNBOOK_MARK_WITHOUT_ROW = "SELECT c.id, c.requalified_at, r.marked_at FROM"
			+ " survey_checklists c LEFT JOIN survey_checklist_requalifications r ON r.checklist_id = c.id"
			+ " WHERE c.requalified_at IS DISTINCT FROM r.marked_at";
	private static final String RUNBOOK_ROLLBACK = "SELECT (SELECT count(*) FROM survey_checklist_requalifications) AS"
			+ " rows, (SELECT count(*) FROM survey_checklists WHERE requalified_at IS NOT NULL) AS marks, (SELECT count(*)"
			+ " FROM owner_lead_activities WHERE type = 'REQUALIFICATION_MARKED') AS history";

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(1000);
	private JdbcTemplate jdbc;
	private String oldLead;
	private List<Map<String, Object>> oldChecklistsBefore;
	private List<Map<String, Object>> oldChecklistsAfter;

	@BeforeAll
	void migrateOldDatabaseWithRowsThenRequalification() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(PREVIOUS_VERSION).load()
				.migrate();
		oldLead = newLead();
		passed(newChecklist(oldLead, 1, newVisit(oldLead, 1, 1), 1));
		newChecklist(oldLead, 2, newVisit(oldLead, 2, 1), 1);
		oldChecklistsBefore = oldChecklists();

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		oldChecklistsAfter = oldChecklists();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	// ==================== populated data (spec 5.2) ====================

	@Test
	void migrate_populatedSchema_keepsRowsAndValidatesEveryConstraint() {
		assertEquals(List.of(), jdbc.queryForList("SELECT conrelid::regclass::text || '.' || conname"
				+ " FROM pg_constraint WHERE NOT convalidated", String.class));

		assertFalse(oldChecklistsBefore.isEmpty(), "no old row: the comparison proves nothing");
		for (Map<String, Object> row : oldChecklistsAfter) {
			assertTrue(row.containsKey("requalified_at"), "survey_checklists has no requalified_at");
			assertNull(row.remove("requalified_at"), "an old checklist got a mark");
		}
		assertEquals(oldChecklistsBefore, oldChecklistsAfter);
	}

	@Test
	void previousServerColumnList_stillInserts() {
		String lead = newLead();
		String visit = newVisit(lead, 1, 1);

		// The columns of the previous entity: the mark stays NULL, so a row it writes counts as unmarked
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version)"
				+ " VALUES (?, ?, 1, ?, 1, ?, 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'DRAFT', ?, ?, 0)", id("CHKL"), lead,
				visit, SALE, SALE, T0);

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM survey_checklists WHERE lead_id = ?"
				+ " AND requalified_at IS NULL", Integer.class, lead));
	}

	/** spec 5.2: the query run before the deploy finds the duplicate, and what a failure of the index leaves behind. */
	@Test
	void migrate_twoPassedOfOneCycle_failsAtTheIndex_andARetryFinishesIt() {
		inNewDatabase("two_passed", dataSource -> {
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(PREVIOUS_VERSION).load()
					.migrate();
			String lead = newLead();
			String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
			String second = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
			passed(first);
			passed(second);
			assertEquals(1, jdbc.queryForList(RUNBOOK_DUPLICATES).size(), "the runbook query must find the duplicate");
			Flyway latest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

			FlywayException failure = assertThrows(FlywayException.class, latest::migrate);

			assertTrue(failure.getMessage().contains(UNIQUE_VIOLATION), failure.getMessage());
			assertEquals(List.of("202611051940", "202611051941"), jdbc.queryForList("SELECT version FROM"
					+ " flyway_schema_history WHERE version >= '202611051940' ORDER BY installed_rank", String.class));
			assertEquals(0, currentPassedIndexes());
			// a fixture only, not a repair path: it leaves a mark without its row
			mark(first);

			latest.migrate();

			assertEquals(1, currentPassedIndexes());
		});
	}

	/** scripts/ops/README.md section 7, verbatim: the checks of the deploy on a schema in order and on a lone mark. */
	@Test
	void runbookQueries_passOnPairedRows_andFindALoneMark() {
		inNewDatabase("reconciliation", dataSource -> {
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
			String lead = newLead();
			String paired = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
			passed(paired);
			mark(paired);
			insert("survey_checklist_requalifications", requalification(paired, "WRONG_ASSET"));

			assertEquals(List.of(), jdbc.queryForList(RUNBOOK_DUPLICATES.replace("status = 'PASSED'",
					"status = 'PASSED' AND requalified_at IS NULL")));
			assertEquals(List.of(), jdbc.queryForList(RUNBOOK_MARK_WITHOUT_ROW));
			assertEquals(List.of(), jdbc.queryForList(RUNBOOK_LOCKS));
			assertEquals(List.of(), jdbc.queryForList("SELECT conrelid::regclass, conname FROM pg_constraint"
					+ " WHERE NOT convalidated"));
			assertEquals(3, jdbc.queryForList("SELECT version, success, execution_time FROM flyway_schema_history WHERE"
					+ " version IN ('202611051940', '202611051941', '202611051942')").size());
			assertEquals(Map.of("indisvalid", true, "indisready", true, "indisunique", true), jdbc.queryForMap(
					"SELECT i.indisvalid, i.indisready, i.indisunique FROM pg_index i WHERE i.indexrelid ="
							+ " 'uq_survey_checklists_current_passed_per_cycle'::regclass"));
			Map<String, Object> rollback = jdbc.queryForMap(RUNBOOK_ROLLBACK);
			assertEquals(1L, rollback.get("rows"));
			assertEquals(1L, rollback.get("marks"));
			assertEquals(0L, rollback.get("history"));

			String lone = newChecklist(lead, 2, newVisit(lead, 2, 2), 2);
			passed(lone);
			mark(lone);

			assertEquals(1, jdbc.queryForList(RUNBOOK_MARK_WITHOUT_ROW).size());
		});
	}

	// ==================== one current PASSED (spec I-1, S-1) ====================

	@Test
	void schema_secondUnmarkedPassed_refused() {
		String lead = newLead();
		String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String second = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
		passed(first);

		assertEquals(UNIQUE_VIOLATION, sqlStateOf(() -> passed(second)));
	}

	@Test
	void schema_markedPlusUnmarked_allowed() {
		String lead = newLead();
		String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String second = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
		passed(first);
		mark(first);

		passed(second);

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM survey_checklists WHERE lead_id = ?"
				+ " AND status = 'PASSED'", Integer.class, lead));
	}

	@Test
	void schema_unmarkingNextToACurrentPassed_refused() {
		String lead = newLead();
		String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String second = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
		passed(first);
		mark(first);
		passed(second);

		assertEquals(UNIQUE_VIOLATION, sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET requalified_at = NULL"
				+ " WHERE id = ?", first)));
	}

	@Test
	void schema_unmarkedPassedInTwoCycles_allowed() {
		String lead = newLead();
		String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String second = newChecklist(lead, 2, newVisit(lead, 2, 2), 2);
		passed(first);

		passed(second);

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM survey_checklists WHERE lead_id = ?"
				+ " AND status = 'PASSED' AND requalified_at IS NULL", Integer.class, lead));
	}

	@Test
	void schema_requalifiedOnNonPassed_refused() {
		String lead = newLead();
		String draft = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);

		assertEquals(CHECK_VIOLATION, sqlStateOf(() -> mark(draft)));
	}

	/** spec I-2: a mark stays on a PASSED revision; the status of a marked revision cannot move. */
	@Test
	void schema_markedRevisionMovedToAnotherStatus_refused() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		passed(checklist);
		mark(checklist);

		assertEquals(CHECK_VIOLATION, sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET status = 'FAILED'"
				+ " WHERE id = ?", checklist)));
	}

	// ==================== the requalification row (spec 5.2) ====================

	@Test
	void schema_requalificationRow_oneForARevision() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		passed(checklist);
		insert("survey_checklist_requalifications", requalification(checklist, "WRONG_ASSET"));

		assertEquals(UNIQUE_VIOLATION, sqlStateOf(() -> insert("survey_checklist_requalifications",
				requalification(checklist, "OTHER"))));
	}

	@Test
	void schema_requalificationRow_namesARevision() {
		assertEquals(FOREIGN_KEY_VIOLATION, sqlStateOf(() -> insert("survey_checklist_requalifications",
				requalification("01RQLCHKLNOPE0000000000001", "OTHER"))));
	}

	@Test
	void schema_requalificationRow_everyKindOfTheSpec() {
		for (String kind : List.of("WRONG_ASSET", "WRONG_OWNER", "WRONG_PERSON", "WRONG_SCOPE", "NEED_CHANGED",
				"PHOTO_PROBLEM", "HAZARD", "OTHER")) {
			String lead = newLead();
			String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
			passed(checklist);

			insert("survey_checklist_requalifications", requalification(checklist, kind));

			assertEquals(kind, jdbc.queryForObject("SELECT reason_kind FROM survey_checklist_requalifications"
					+ " WHERE checklist_id = ?", String.class, checklist));
		}
	}

	@Test
	void schema_requalificationRow_unknownKind_refused() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		passed(checklist);

		assertEquals(CHECK_VIOLATION, sqlStateOf(() -> insert("survey_checklist_requalifications",
				requalification(checklist, "CHANGED_MIND"))));
	}

	// ==================== rows ====================

	/** Runs the body with {@link #jdbc} on a new database of the container, which no other test shares. */
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

	private int currentPassedIndexes() {
		return jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname ="
				+ " 'uq_survey_checklists_current_passed_per_cycle'", Integer.class);
	}

	private void passed(String checklist) {
		jdbc.update("UPDATE survey_checklists SET status = 'PASSED', submitted_at = ?, submitted_by_user_id = ?,"
				+ " decided_at = ? WHERE id = ?", T0, SALE, T0.plusHours(1), checklist);
	}

	private void mark(String checklist) {
		jdbc.update("UPDATE survey_checklists SET requalified_at = ? WHERE id = ?", T0.plusDays(1), checklist);
	}

	private static Map<String, Object> requalification(String checklist, String kind) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("checklist_id", checklist);
		row.put("reason_kind", kind);
		row.put("reason", "Sai tài sản");
		row.put("event_source", "Chủ nhà báo");
		row.put("scope", "Toàn bộ bản khảo sát");
		row.put("basis_date", LocalDate.of(2026, 11, 5));
		row.put("marked_by_user_id", MANAGER);
		row.put("marked_at", T0.plusDays(1));
		row.put("lead_status_before", "QUALIFIED");
		row.put("lead_status_after", "CONTACTED");
		return row;
	}

	private String newChecklist(String lead, int no, String visit, int cycle) {
		String checklist = id("CHKL");
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version)"
				+ " VALUES (?, ?, ?, ?, ?, ?, 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'DRAFT', ?, ?, 0)", checklist, lead, no,
				visit, cycle, SALE, SALE, T0.minusDays(1));
		return checklist;
	}

	private String newLead() {
		String lead = id("LEAD");
		String tail = lead.substring(lead.length() - 6);
		String consentId = "01RQLCONSENT00000000" + tail;
		jdbc.update("INSERT INTO consent_records (id, notice_version, purpose, channel, accepted_at)"
				+ " VALUES (?, 'owner-intake-2026-10', 'OWNER_INTAKE', 'WEB', ?)", consentId, T0.minusDays(10));
		jdbc.update("INSERT INTO owner_leads (id, request_code, status, intent, source, contact_name,"
				+ " phone_normalized, email, province_code, province_name, ward_code, ward_name, address_detail,"
				+ " address_key, latitude, longitude, property_type, asking_price_amount, asking_price_unit,"
				+ " available_from, notes, preferred_callback_time, consent_record_id, submission_snapshot,"
				+ " assigned_user_id, next_action_at, duplicate_of_lead_id, close_reason, created_at, updated_at,"
				+ " version, intent_cycle)"
				+ " VALUES (?, ?, 'QUALIFIED', 'LONG_TERM_RENT', 'WEB', 'Chủ nhà', ?, 'chu.nha@example.vn', '48',"
				+ " 'Đà Nẵng', '20242', 'Hải Châu', '1 Bạch Đằng', ?, 16.067800, 108.220800, 'APARTMENT', 15000000,"
				+ " 'MONTH', DATE '2026-12-01', 'Có nội thất', 'Buổi tối', ?, '{}', ?, ?, NULL, NULL, ?, ?, 3, 1)",
				lead, "SVQ-" + tail, "+84900" + tail, "1 bach dang " + lead, consentId, SALE, T0.plusDays(1),
				T0.minusDays(10), T0.minusDays(1));
		return lead;
	}

	private String newVisit(String lead, int no, int cycle) {
		String visit = id("APPT");
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", visit);
		row.put("lead_id", lead);
		row.put("appointment_no", no);
		row.put("kind", "FIRST");
		row.put("intent", "LONG_TERM_RENT");
		row.put("intent_cycle", cycle);
		row.put("status", "VISITED");
		row.put("start_at", T0.minusDays(2));
		row.put("duration_minutes", 60);
		row.put("meeting_address", "1 Bạch Đằng");
		row.put("meeting_province_code", "48");
		row.put("meeting_province_name", "Đà Nẵng");
		row.put("meeting_ward_code", "20242");
		row.put("meeting_ward_name", "Hải Châu");
		row.put("meeting_latitude", new BigDecimal("16.067800"));
		row.put("meeting_longitude", new BigDecimal("108.220800"));
		row.put("access_note", "Bấm chuông");
		row.put("on_site_contact_name", "Anh Tuấn");
		row.put("on_site_contact_role", "Bảo vệ");
		row.put("current_revision_no", 1);
		row.put("lead_address_detail", "1 Bạch Đằng");
		row.put("lead_province_code", "48");
		row.put("lead_province_name", "Đà Nẵng");
		row.put("lead_ward_code", "20242");
		row.put("lead_ward_name", "Hải Châu");
		row.put("lead_latitude", new BigDecimal("16.067800"));
		row.put("lead_longitude", new BigDecimal("108.220800"));
		row.put("surveyor_user_id", SALE);
		row.put("confirmation_state", "CONFIRMED");
		row.put("ever_confirmed", true);
		row.put("confirmation_changed_by", SALE);
		row.put("confirmation_changed_at", T0.minusDays(3));
		row.put("confirmation_note", "Đã gọi");
		row.put("created_by", SALE);
		row.put("created_at", T0.minusDays(3));
		row.put("updated_at", T0.minusDays(1));
		row.put("version", 2L);
		row.put("visit_record_kind", "OWN");
		row.put("performed_by", SALE);
		row.put("arrived_at", T0.minusDays(2));
		row.put("visit_revision_no", 1);
		row.put("visit_note", "Đã khảo sát");
		row.put("visit_recorded_by", SALE);
		row.put("visit_recorded_at", T0.minusDays(2).plusHours(1));
		insert("survey_appointments", row);
		return visit;
	}

	// ==================== helpers ====================

	private void insert(String table, Map<String, Object> row) {
		jdbc.update("INSERT INTO " + table + " (" + String.join(", ", row.keySet()) + ") VALUES ("
				+ String.join(", ", Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
	}

	/** The SQLSTATE of the statement, or null when it succeeded. */
	private static String sqlStateOf(Runnable statement) {
		try {
			statement.run();
			return null;
		} catch (DataAccessException e) {
			Throwable cause = e;
			while (cause != null && !(cause instanceof SQLException)) {
				cause = cause.getCause();
			}
			return cause == null ? e.toString() : ((SQLException) cause).getSQLState();
		}
	}

	/** A fresh 26-character id with the marker. */
	private String id(String marker) {
		return String.format("01RQL%s%017d", marker, sequence.incrementAndGet());
	}

	private List<Map<String, Object>> oldChecklists() {
		return new ArrayList<>(jdbc.queryForList("SELECT * FROM survey_checklists WHERE lead_id = ? ORDER BY id",
				oldLead));
	}
}
