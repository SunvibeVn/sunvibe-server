package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * The checklist submission and review migrations (survey-checklist submission spec 5.3, 7.4, S-19) on a real PostgreSQL
 * holding W16/W17 data: the database is migrated to the last media version, filled, then migrated to the latest
 * version. Every constraint ends validated, every old row stays as it was, each widened CHECK is an exact superset of
 * the old one and the previous server's inserts are still accepted. Needs Docker, like
 * {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistReviewSchemaPostgresTest {

	/** The W17 schema: the last version before this slice. */
	private static final String W17_VERSION = "202611021404";

	private static final List<String> OLD_TABLES = List.of("owner_leads", "owner_lead_activities", "survey_appointments",
			"survey_checklists", "survey_checklist_groups", "survey_checklist_takeovers", "survey_security_events",
			"survey_media_sets", "survey_media");
	/** The columns added to the old tables since then, by this slice and the requalification; old rows keep them NULL. */
	private static final Set<String> NEW_CHECKLIST_COLUMNS = Set.of("previous_checklist_id", "submitted_at",
			"submitted_by_user_id", "decided_at", "requalified_at");

	/** Constraint name to the values this slice adds (spec 5.3). */
	private static final Map<String, List<String>> WIDENED = new LinkedHashMap<>();

	static {
		WIDENED.put("chk_survey_checklists_status", List.of("SUBMITTED", "PASSED", "NEEDS_CHANGES", "FAILED"));
		WIDENED.put("chk_survey_checklist_takeovers_kind", List.of("REVISION_BY_OTHER"));
		WIDENED.put("chk_survey_security_events_action", List.of("SUBMIT", "REVIEW", "REVISION", "DESELECT"));
		WIDENED.put("chk_survey_security_events_outcome", List.of("NOT_INDEPENDENT"));
	}

	private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 3, 9, 0, 0, 0, ZoneOffset.UTC);
	private static final String SALE = "01REVSALE00000000000000001";
	private static final String OTHER_SALE = "01REVSALE00000000000000002";
	private static final String MANAGER = "01REVMGR000000000000000001";
	private static final String OLD_LEAD = "01REVLEAD00000000000000001";
	private static final String OLD_VISIT = "01REVAPPT00000000000000001";
	private static final String OLD_CHECKLIST = "01REVCHKL00000000000000001";
	private static final String OLD_MEDIA = "01REVMEDIA0000000000000001";
	private static final String SHA = "a".repeat(64);

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(1000);
	private JdbcTemplate jdbc;
	private Map<String, List<Map<String, Object>>> oldRowsBefore;
	private Map<String, List<Map<String, Object>>> oldRowsAfter;
	private Map<String, String> widenedDefinitionsBefore;

	@BeforeAll
	void migrateW17DatabaseWithRowsThenReview() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(W17_VERSION).load()
				.migrate();
		insertW17Rows();
		oldRowsBefore = oldRows();
		widenedDefinitionsBefore = widenedDefinitions();

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		oldRowsAfter = oldRows();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	// ==================== populated data (spec 7.4, S-19) ====================

	@Test
	void migrate_populatedW17Schema_keepsRowsAndValidatesEveryConstraint() {
		assertEquals(List.of(), jdbc.queryForList("SELECT conrelid::regclass::text || '.' || conname"
				+ " FROM pg_constraint WHERE NOT convalidated", String.class));

		for (Map<String, Object> row : oldRowsAfter.get("survey_checklists")) {
			for (String column : NEW_CHECKLIST_COLUMNS) {
				assertTrue(row.containsKey(column), "survey_checklists has no " + column);
				assertNull(row.remove(column), "an old checklist got a value in " + column);
			}
		}
		for (Map<String, Object> row : oldRowsAfter.get("survey_security_events")) {
			assertNull(row.get("request_id"));
		}
		assertEquals(oldRowsBefore, oldRowsAfter);
		for (String table : OLD_TABLES) {
			assertTrue(!oldRowsBefore.get(table).isEmpty(), "no row in " + table + ": the comparison proves nothing");
		}
	}

	@Test
	void widenedChecks_areExactSupersets() {
		Map<String, String> after = widenedDefinitions();
		for (Map.Entry<String, List<String>> widened : WIDENED.entrySet()) {
			String name = widened.getKey();
			Set<String> expected = new TreeSet<>(quotedValues(widenedDefinitionsBefore.get(name)));
			assertTrue(Collections.disjoint(expected, widened.getValue()), name + " already held a new value");
			expected.addAll(widened.getValue());

			assertEquals(expected, new TreeSet<>(quotedValues(after.get(name))), name);
		}
	}

	@Test
	void referenceDetailCheck_stillNeedsItsReference() {
		assertEquals("23514", sqlStateOf(() -> jdbc.update("INSERT INTO owner_lead_activities (id, lead_id, type,"
				+ " occurred_at, reference_detail) VALUES (?, ?, 'CHECKLIST_REVIEWED', ?, 'PASSED')", nextId(), OLD_LEAD,
				T0)));
	}

	@Test
	void previousServerColumnLists_stillInsert() {
		String lead = newLead();
		String visit = newVisit(lead, 1, 1);
		// The columns of the 7b4f0be entity: the previous server keeps running during the roll-out (spec 7.4)
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version)"
				+ " VALUES (?, ?, 1, ?, 1, ?, 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'DRAFT', ?, ?, 0)",
				id("CHKL"), lead, visit, SALE, SALE, T0);
		jdbc.update("INSERT INTO owner_lead_activities (id, lead_id, type, occurred_at, reference_id, reference_no,"
				+ " reference_detail) VALUES (?, ?, 'CHECKLIST_SAVED', ?, ?, 1, 'TERMS')", nextId(), lead, T0, visit);
	}

	// ==================== the revision row (spec 5.3) ====================

	@Test
	void schema_statusAcceptsNeedsChanges() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		jdbc.update("UPDATE survey_checklists SET status = 'NEEDS_CHANGES', submitted_at = ?, submitted_by_user_id = ?,"
				+ " decided_at = ? WHERE id = ?", T0, SALE, T0.plusHours(1), checklist);

		assertEquals("NEEDS_CHANGES", jdbc.queryForObject("SELECT status FROM survey_checklists WHERE id = ?",
				String.class, checklist));
	}

	@Test
	void schema_unknownStatus_refused() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);

		assertEquals("23514", sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET status = 'REOPENED',"
				+ " submitted_at = ?, submitted_by_user_id = ? WHERE id = ?", T0, SALE, checklist)));
	}

	@Test
	void schema_submittedStatusWithoutSubmitter_refused() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);

		assertEquals("23514", sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET status = 'SUBMITTED'"
				+ " WHERE id = ?", checklist)));
		assertEquals("23514", sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET status = 'SUBMITTED',"
				+ " submitted_at = ? WHERE id = ?", T0, checklist)));
	}

	@Test
	void schema_decidedStatusWithoutDecisionTime_refused() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);

		assertEquals("23514", sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET status = 'PASSED',"
				+ " submitted_at = ?, submitted_by_user_id = ? WHERE id = ?", T0, SALE, checklist)));
	}

	@Test
	void schema_twoSubmittedSameCycle_refused() {
		String lead = newLead();
		String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String second = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
		submit(first);

		assertEquals("23505", sqlStateOf(() -> submit(second)));
	}

	@Test
	void schema_submittedInTwoCycles_allowed() {
		String lead = newLead();
		String first = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String second = newChecklist(lead, 2, newVisit(lead, 2, 2), 2);
		submit(first);

		submit(second);

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM survey_checklists WHERE lead_id = ?"
				+ " AND status = 'SUBMITTED'", Integer.class, lead));
	}

	@Test
	void schema_previousChecklist_namesAChecklist() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);

		assertEquals("23503", sqlStateOf(() -> jdbc.update("UPDATE survey_checklists SET previous_checklist_id = ?"
				+ " WHERE id = ?", "01REVCHKLNOPE0000000000001", checklist)));
	}

	// ==================== submission, evidence, decision, marks, selection (spec 5.3) ====================

	@Test
	void schema_evidencePrimaryKey_refusesDuplicate() {
		Fixture fixture = submittedWithPhoto();
		insertEvidence(fixture);

		assertEquals("23505", sqlStateOf(() -> insertEvidence(fixture)));
	}

	@Test
	void schema_submissionMediaPolicy_onlyTheV1Value() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		Map<String, Object> row = submission(checklist);
		row.put("media_policy", "SOME_POLICY");

		assertEquals("23514", sqlStateOf(() -> insert("survey_checklist_submissions", row)));
	}

	@Test
	void schema_submissionHash_mustBeHex() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		Map<String, Object> row = submission(checklist);
		row.put("content_sha256", "XYZ");

		assertEquals("23514", sqlStateOf(() -> insert("survey_checklist_submissions", row)));
	}

	@Test
	void schema_decisionRows_followTheirOutcome() {
		String passed = submittedChecklist();
		insert("survey_checklist_decisions", decision(passed, "PASSED", null, null, null, "Thiếu giấy tờ gốc"));
		String returned = submittedChecklist();
		insert("survey_checklist_decisions", decision(returned, "NEEDS_CHANGES", "RESURVEY_REQUIRED", null, "Lý do",
				null));
		String failed = submittedChecklist();
		insert("survey_checklist_decisions", decision(failed, "FAILED", null, "OUT_OF_SCOPE", "Lý do", null));

		for (Map<String, Object> wrong : List.of(
				decision(submittedChecklist(), "PASSED", "DOCUMENTS_ONLY", null, null, null),
				decision(submittedChecklist(), "PASSED", null, "TEMPORARY", null, null),
				decision(submittedChecklist(), "NEEDS_CHANGES", null, null, "Lý do", null),
				decision(submittedChecklist(), "NEEDS_CHANGES", "DOCUMENTS_ONLY", null, null, null),
				decision(submittedChecklist(), "NEEDS_CHANGES", "DOCUMENTS_ONLY", null, "Lý do", "Giới hạn"),
				decision(submittedChecklist(), "FAILED", null, null, "Lý do", null),
				decision(submittedChecklist(), "FAILED", null, "TEMPORARY", null, null),
				decision(submittedChecklist(), "FAILED", "DOCUMENTS_ONLY", "TEMPORARY", "Lý do", null),
				decision(submittedChecklist(), "APPROVED", null, null, null, null))) {
			assertEquals("23514", sqlStateOf(() -> insert("survey_checklist_decisions", wrong)), wrong.toString());
		}
	}

	@Test
	void schema_rejection_namesAFrozenPhotoOfTheDecision() {
		Fixture fixture = submittedWithPhoto();
		insertEvidence(fixture);
		insert("survey_checklist_decisions", decision(fixture.checklist(), "NEEDS_CHANGES", "DOCUMENTS_ONLY", null,
				"Lý do", null));

		jdbc.update("INSERT INTO survey_checklist_evidence_rejections (checklist_id, media_id, reason)"
				+ " VALUES (?, ?, 'Ảnh mờ')", fixture.checklist(), fixture.media());
		assertEquals("23503", sqlStateOf(() -> jdbc.update("INSERT INTO survey_checklist_evidence_rejections"
				+ " (checklist_id, media_id, reason) VALUES (?, ?, 'Ảnh mờ')", fixture.checklist(), OLD_MEDIA)));
	}

	@Test
	void schema_selection_deselectionColumnsAllSetOrAllEmpty() {
		Fixture fixture = submittedWithPhoto();
		String lead = fixture.lead();
		String next = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
		Map<String, Object> row = selection(next, fixture);
		insert("survey_checklist_media_selections", row);
		String update = "UPDATE survey_checklist_media_selections SET ";
		String where = " WHERE checklist_id = ? AND media_id = ?";

		assertEquals("23514", sqlStateOf(() -> jdbc.update(update + "deselected_at = ?" + where, T0, next,
				fixture.media())), "a deselection needs who");
		assertEquals("23514", sqlStateOf(() -> jdbc.update(update + "deselected_at = ?, deselected_by_user_id = ?"
				+ where, T0, SALE, next, fixture.media())), "a deselection needs its kind");
		assertEquals("23514", sqlStateOf(() -> jdbc.update(update + "deselected_at = ?, deselected_by_user_id = ?,"
				+ " deselection_kind = 'CASCADE'" + where, T0, SALE, next, fixture.media())),
				"a cascade names the save");
		assertEquals("23514", sqlStateOf(() -> jdbc.update(update + "deselected_at = ?, deselected_by_user_id = ?,"
				+ " deselection_kind = 'HOLDER', deselection_correction_reason = 'Lý do'" + where, T0, SALE, next,
				fixture.media())), "half a correction");

		jdbc.update(update + "deselected_at = ?, deselected_by_user_id = ?, deselection_kind = 'HOLDER'" + where, T0,
				SALE, next, fixture.media());
	}

	@Test
	void schema_selectionPrimaryKey_refusesDuplicate() {
		Fixture fixture = submittedWithPhoto();
		String lead = fixture.lead();
		String next = newChecklist(lead, 2, newVisit(lead, 2, 1), 1);
		insert("survey_checklist_media_selections", selection(next, fixture));

		assertEquals("23505", sqlStateOf(() -> insert("survey_checklist_media_selections", selection(next, fixture))));
	}

	@Test
	void schema_revisionByOtherTakeover_fromUserPreviousHolder() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String insert = "INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id,"
				+ " to_user_id, reason, basis_confirmed_on, basis_text, snapshot, occurred_at)"
				+ " VALUES (?, ?, ?, 'REVISION_BY_OTHER', ?, ?, 'Người khác', ?, 'Trưởng nhóm giao', NULL, ?)";

		jdbc.update(insert, nextId(), checklist, 1, SALE, OTHER_SALE, LocalDate.of(2026, 11, 2), T0);

		assertEquals("23514", sqlStateOf(() -> jdbc.update(insert, nextId(), checklist, 2, null, OTHER_SALE,
				LocalDate.of(2026, 11, 2), T0)), "plan PD-9: the row names the previous holder");
	}

	@Test
	void schema_referenceDetail40_acceptsNeedsChangesResurvey() {
		String detail = "NEEDS_CHANGES:RESURVEY_REQUIRED";
		long id = nextId();

		jdbc.update("INSERT INTO owner_lead_activities (id, lead_id, type, occurred_at, reference_id, reference_no,"
				+ " reference_detail) VALUES (?, ?, 'CHECKLIST_REVIEWED', ?, ?, 1, ?)", id, OLD_LEAD, T0, OLD_CHECKLIST,
				detail);

		assertEquals(detail, jdbc.queryForObject("SELECT reference_detail FROM owner_lead_activities WHERE id = ?",
				String.class, id));
	}

	@Test
	void schema_newSecurityEventValues_insert() {
		for (String action : WIDENED.get("chk_survey_security_events_action")) {
			jdbc.update("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id, action,"
					+ " outcome, occurred_at) VALUES (?, ?, ?, 'CHECKLIST', ?, ?, 'NOT_INDEPENDENT', ?)", nextId(),
					OTHER_SALE, OLD_LEAD, OLD_CHECKLIST, action, T0);
		}
	}

	// ==================== rows ====================

	private record Fixture(String lead, String checklist, String media) {
	}

	private void insertW17Rows() {
		insertLead(OLD_LEAD);
		insert("survey_appointments", visited(appointment(OLD_VISIT, OLD_LEAD, 1, 1)));
		insertChecklist(OLD_CHECKLIST, OLD_LEAD, 1, OLD_VISIT, 1);
		jdbc.update("INSERT INTO owner_lead_activities (id, lead_id, type, occurred_at, reference_id, reference_no,"
				+ " reference_detail) VALUES (?, ?, 'CHECKLIST_SAVED', ?, ?, 1, 'TERMS')", nextId(), OLD_LEAD, T0,
				OLD_CHECKLIST);
		jdbc.update("INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id,"
				+ " to_user_id, reason, basis_confirmed_on, basis_text, snapshot, occurred_at)"
				+ " VALUES (?, ?, 1, 'START_BY_OTHER', NULL, ?, 'Người khảo sát nghỉ', ?, 'Trưởng nhóm giao', NULL, ?)",
				nextId(), OLD_CHECKLIST, SALE, LocalDate.of(2026, 11, 1), T0);
		jdbc.update("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id, action,"
				+ " outcome, correlation_id, occurred_at) VALUES (?, ?, ?, 'CHECKLIST', ?, 'SAVE', 'NOT_ASSIGNEE', 'c-1', ?)",
				nextId(), OTHER_SALE, OLD_LEAD, OLD_CHECKLIST, T0);
		insertMedia(OLD_CHECKLIST, OLD_MEDIA, 1);
	}

	private String submittedChecklist() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		submit(checklist);
		insert("survey_checklist_submissions", submission(checklist));
		return checklist;
	}

	private Fixture submittedWithPhoto() {
		String lead = newLead();
		String checklist = newChecklist(lead, 1, newVisit(lead, 1, 1), 1);
		String media = id("MEDI");
		insertMedia(checklist, media, 1);
		submit(checklist);
		insert("survey_checklist_submissions", submission(checklist));
		return new Fixture(lead, checklist, media);
	}

	private void submit(String checklist) {
		jdbc.update("UPDATE survey_checklists SET status = 'SUBMITTED', submitted_at = ?, submitted_by_user_id = ?"
				+ " WHERE id = ?", T0, SALE, checklist);
	}

	private void insertEvidence(Fixture fixture) {
		jdbc.update("INSERT INTO survey_checklist_evidence (checklist_id, media_id, category, space_id, space_code,"
				+ " full_sha256) VALUES (?, ?, 'EXTERIOR_ACCESS', NULL, NULL, ?)", fixture.checklist(), fixture.media(),
				SHA);
	}

	private Map<String, Object> submission(String checklist) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("checklist_id", checklist);
		row.put("submitted_by_user_id", SALE);
		row.put("submitted_at", T0);
		row.put("template_code", "LONG_TERM_RENT");
		row.put("template_version", 1);
		row.put("kind", "FULL_ASSESSMENT");
		row.put("lead_intent", "LONG_TERM_RENT");
		row.put("intent_cycle", 1);
		row.put("visit_appointment_id", OLD_VISIT);
		row.put("visit_performed_by", SALE);
		row.put("visit_arrived_at", T0.minusDays(1));
		row.put("visit_recorded_by", SALE);
		row.put("visit_recorded_at", T0.minusDays(1));
		row.put("rental_scope", "WHOLE");
		row.put("property_type", "APARTMENT");
		row.put("spaces", "[]");
		row.put("group_versions", "{}");
		row.put("media_policy", "MEDIA_V1_NO_PERMISSION");
		row.put("content_sha256", SHA);
		return row;
	}

	private Map<String, Object> decision(String checklist, String outcome, String needsChangesKind, String failedKind,
										 String reason, String limitations) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("checklist_id", checklist);
		row.put("outcome", outcome);
		row.put("needs_changes_kind", needsChangesKind);
		row.put("failed_kind", failedKind);
		row.put("reason", reason);
		row.put("notes", null);
		row.put("limitations", limitations);
		row.put("reviewer_user_id", MANAGER);
		row.put("decided_at", T0.plusHours(1));
		row.put("lead_status_before", "SURVEY_SCHEDULED");
		row.put("lead_status_after", "QUALIFIED");
		return row;
	}

	private Map<String, Object> selection(String checklist, Fixture carried) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("checklist_id", checklist);
		row.put("media_id", carried.media());
		row.put("carried_from_checklist_id", carried.checklist());
		row.put("selected_at", T0);
		return row;
	}

	private String newLead() {
		String lead = id("LEAD");
		insertLead(lead);
		return lead;
	}

	private String newVisit(String lead, int no, int cycle) {
		String visit = id("APPT");
		insert("survey_appointments", visited(appointment(visit, lead, no, cycle)));
		return visit;
	}

	private String newChecklist(String lead, int no, String visit, int cycle) {
		String checklist = id("CHKL");
		insertChecklist(checklist, lead, no, visit, cycle);
		return checklist;
	}

	private void insertChecklist(String id, String lead, int no, String visit, int cycle) {
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version)"
				+ " VALUES (?, ?, ?, ?, ?, ?, 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'DRAFT', ?, ?, 0)", id, lead, no,
				visit, cycle, SALE, SALE, T0.minusDays(1));
		jdbc.update("INSERT INTO survey_checklist_groups (checklist_id, group_key, content, version) VALUES"
				+ " (?, 'IDENTIFICATION', '{}', 0)", id);
	}

	private void insertMedia(String checklist, String media, int no) {
		jdbc.update("INSERT INTO survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " VALUES (?, 1, 1, ?)", checklist, T0);
		jdbc.update("INSERT INTO survey_media (id, checklist_id, upload_key, media_no, category, uploaded_by_user_id,"
				+ " created_at, declared_size_bytes, source_sha256, status) VALUES (?, ?, ?, ?, 'EXTERIOR_ACCESS', ?, ?,"
				+ " 100, ?, 'AWAITING_CONTENT')", media, checklist, id("UPLD"), no, SALE, T0, SHA);
	}

	private void insertLead(String leadId) {
		String tail = leadId.substring(leadId.length() - 6);
		String consentId = "01REVCONSENT00000000" + tail;
		jdbc.update("INSERT INTO consent_records (id, notice_version, purpose, channel, accepted_at)"
				+ " VALUES (?, 'owner-intake-2026-10', 'OWNER_INTAKE', 'WEB', ?)", consentId, T0.minusDays(10));
		jdbc.update("INSERT INTO owner_leads (id, request_code, status, intent, source, contact_name,"
				+ " phone_normalized, email, province_code, province_name, ward_code, ward_name, address_detail,"
				+ " address_key, latitude, longitude, property_type, asking_price_amount, asking_price_unit,"
				+ " available_from, notes, preferred_callback_time, consent_record_id, submission_snapshot,"
				+ " assigned_user_id, next_action_at, duplicate_of_lead_id, close_reason, created_at, updated_at,"
				+ " version, intent_cycle)"
				+ " VALUES (?, ?, 'SURVEY_SCHEDULED', 'LONG_TERM_RENT', 'WEB', 'Chủ nhà', ?, 'chu.nha@example.vn', '48',"
				+ " 'Đà Nẵng', '20242', 'Hải Châu', '1 Bạch Đằng', ?, 16.067800, 108.220800, 'APARTMENT', 15000000,"
				+ " 'MONTH', DATE '2026-12-01', 'Có nội thất', 'Buổi tối', ?, '{}', ?, ?, NULL, NULL, ?, ?, 3, 1)",
				leadId, "SVM-" + tail, "+84900" + tail, "1 bach dang " + leadId, consentId, SALE, T0.plusDays(1),
				T0.minusDays(10), T0.minusDays(1));
	}

	private Map<String, Object> appointment(String id, String leadId, int no, int cycle) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id);
		row.put("lead_id", leadId);
		row.put("appointment_no", no);
		row.put("kind", "FIRST");
		row.put("previous_visit_id", null);
		row.put("resurvey_reason", null);
		row.put("intent", "LONG_TERM_RENT");
		row.put("intent_cycle", cycle);
		row.put("status", "SCHEDULED");
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
		row.put("location_reason", null);
		row.put("surveyor_user_id", SALE);
		row.put("confirmation_state", "CONFIRMED");
		row.put("confirmation_cause", null);
		row.put("ever_confirmed", true);
		row.put("confirmation_changed_by", SALE);
		row.put("confirmation_changed_at", T0.minusDays(3));
		row.put("confirmation_note", "Đã gọi");
		row.put("created_by", SALE);
		row.put("created_at", T0.minusDays(3));
		row.put("updated_at", T0.minusDays(1));
		row.put("version", 2L);
		return row;
	}

	private static Map<String, Object> visited(Map<String, Object> row) {
		row.put("status", "VISITED");
		row.put("visit_record_kind", "OWN");
		row.put("performed_by", SALE);
		row.put("arrived_at", T0.minusDays(2));
		row.put("visit_revision_no", 1);
		row.put("visit_note", "Đã khảo sát");
		row.put("visit_recorded_by", SALE);
		row.put("visit_recorded_at", T0.minusDays(2).plusHours(1));
		return row;
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
		return String.format("01REV%s%017d", marker, nextId());
	}

	private long nextId() {
		return sequence.incrementAndGet();
	}

	private Map<String, List<Map<String, Object>>> oldRows() {
		Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
		for (String table : OLD_TABLES) {
			rows.put(table, new ArrayList<>(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2")));
		}
		return rows;
	}

	private Map<String, String> widenedDefinitions() {
		Map<String, String> definitions = new LinkedHashMap<>();
		for (String name : WIDENED.keySet()) {
			definitions.put(name, jdbc.queryForObject(
					"SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, name));
		}
		return definitions;
	}

	private static Set<String> quotedValues(String definition) {
		Set<String> values = new LinkedHashSet<>();
		Matcher matcher = Pattern.compile("'([A-Z_]+)'").matcher(definition);
		while (matcher.find()) {
			values.add(matcher.group(1));
		}
		return values;
	}
}
