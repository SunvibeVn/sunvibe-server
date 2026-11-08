package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;
import static vn.sunvibe.survey.SurveyApiTestSupport.ok;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;

/**
 * POST /survey-checklists/{id}/requalification and what it does to the reads and the lead's other commands
 * (property-create requalification spec 4, 5.3, 7, S-1..S-4, S-6, S-9, S-12). Needs Docker.
 */
class ChecklistRequalificationApiPostgresTest extends MediaApiTestBase {

	private static final String SECRET_REASON = "Ly do rieng ve tai san sai";
	private static final String SECRET_SOURCE = "Nguon rieng tu hang xom";
	private static final String SECRET_SCOPE = "Pham vi rieng cua ban khao sat";
	private static final String MARKED = "select count(*) from owner_lead_activities where lead_id = ?"
			+ " and type = 'REQUALIFICATION_MARKED'";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MediaTestStore store;

	@Autowired
	private MediaTestSwitches switches;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private ChecklistReviewApiSupport review;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;
	private TestUser superAdmin;
	private List<TestUser> outsiders;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0970");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Requalify Sale", "SALE");
		otherSale = survey.user("Requalify Other Sale", "SALE");
		leader = survey.user("Requalify Leader", "SALE_LEADER");
		manager = survey.user("Requalify Manager", "SALES_MANAGER");
		admin = survey.user("Requalify Admin", "ADMIN");
		superAdmin = survey.user("Requalify Super", "SUPER_ADMIN");
		outsiders = List.of(survey.user("Requalify Reviewer", "LISTING_REVIEWER"),
				survey.user("Requalify Owner", "OWNER"), survey.user("Requalify Customer", "CUSTOMER"));
	}

	@BeforeEach
	void open() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void reset() {
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== the mark (spec 4, 5.3, S-2, S-3) ====================

	@Test
	void mark_qualified_movesLead_oneHistoryRow() throws Exception {
		Ready ready = passed();
		String id = ready.checklistId();
		int accessLogs = survey.accessLogRows(ready.leadId());

		Result answer = requalify(manager, id);

		ok(answer);
		assertEquals("CONTACTED", survey.leadStatus(ready.leadId()));
		assertEquals("CONTACTED", answer.body().path("lead").path("status").asString());
		assertEquals(survey.version(ready.leadId()), answer.body().path("lead").path("version").asLong());
		JsonNode entry = answer.body().path("historyEntry");
		assertEquals("REQUALIFICATION_MARKED", entry.path("type").asString());
		assertEquals(manager.userId(), entry.path("actorUserId").asString());
		assertEquals("QUALIFIED", entry.path("fromStatus").asString());
		assertEquals("CONTACTED", entry.path("toStatus").asString());
		assertEquals(id, entry.path("referenceId").asString());
		assertEquals(1, entry.path("referenceNo").asInt());
		assertEquals(1, count(MARKED, ready.leadId()));
		Map<String, Object> history = jdbc.queryForMap("select * from owner_lead_activities where lead_id = ?"
				+ " and type = 'REQUALIFICATION_MARKED'", ready.leadId());
		assertEquals(id, history.get("reference_id"));
		assertEquals(1, history.get("reference_no"));
		assertNull(history.get("reference_detail"));
		assertEquals(accessLogs + 1, survey.accessLogRows(ready.leadId()));
		assertNotNull(requalifiedAt(id));
		Map<String, Object> row = requalification(id);
		assertEquals("WRONG_ASSET", row.get("reason_kind"));
		assertEquals(SECRET_REASON, row.get("reason"));
		assertEquals(SECRET_SOURCE, row.get("event_source"));
		assertEquals(SECRET_SCOPE, row.get("scope"));
		assertEquals(today().toString(), row.get("basis_date").toString());
		assertEquals(manager.userId(), row.get("marked_by_user_id"));
		assertEquals("QUALIFIED", row.get("lead_status_before"));
		assertEquals("CONTACTED", row.get("lead_status_after"));
		JsonNode checklist = answer.body().path("checklist");
		assertFalse(checklist.path("passed").path("usable").asBoolean());
		assertEquals("REQUALIFIED", checklist.path("passed").path("reason").asString());
		assertEquals(SECRET_REASON, checklist.path("requalification").path("reason").asString());
	}

	@Test
	void mark_onHold_leadStays() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());
		long version = survey.version(ready.leadId());

		Result answer = requalify(manager, ready.checklistId());

		ok(answer);
		assertEquals("ON_HOLD", survey.leadStatus(ready.leadId()));
		assertEquals(version + 1, survey.version(ready.leadId()));
		assertTrue(answer.body().path("historyEntry").path("fromStatus").isNull(), answer.rawBody());
		assertTrue(answer.body().path("historyEntry").path("toStatus").isNull(), answer.rawBody());
		Map<String, Object> row = requalification(ready.checklistId());
		assertEquals("ON_HOLD", row.get("lead_status_before"));
		assertEquals("ON_HOLD", row.get("lead_status_after"));
	}

	@Test
	void mark_closed_409() throws Exception {
		Ready ready = passed();
		ok(toStatus(ready.leadId(), "WITHDRAWN"));
		Map<String, Object> body = body(ready.checklistId());
		body.put("basisDate", today().minusDays(30).toString());

		Result refused = requalify(manager, ready.checklistId(), body);

		// the lead's status comes before the date (spec 5.3)
		assertError(refused, 409, "INVALID_STATE_TRANSITION", "status");
		assertTrue(refused.body().path("errors").path(0).path("allowedValues").isEmpty(), refused.rawBody());
		assertUnmarked(ready);
	}

	@Test
	void mark_otherLeadStatus_409() throws Exception {
		Ready ready = passed();
		jdbc.update("update owner_leads set status = 'CONTACTED' where id = ?", ready.leadId());

		assertError(requalify(manager, ready.checklistId()), 409, "INVALID_STATE_TRANSITION", "status");
		assertUnmarked(ready);
	}

	/** Seeded by SQL: a requirement change refuses an unmarked PASSED. */
	@Test
	void mark_olderCycle_409() throws Exception {
		Ready ready = passed();
		jdbc.update("update owner_leads set intent_cycle = intent_cycle + 1 where id = ?", ready.leadId());

		assertError(requalify(manager, ready.checklistId()), 409, "SURVEY_NOT_ALLOWED", "checklist");
		assertUnmarked(ready);
	}

	@Test
	void mark_notPassed_409() throws Exception {
		Ready ready = review.ready(leader, sale);

		Result refused = requalify(manager, ready.checklistId());

		assertError(refused, 409, "SURVEY_NOT_ALLOWED", "checklist");
		assertFalse(refused.rawBody().contains("booked"), "the message speaks of this command: " + refused.rawBody());
		assertUnmarked(ready);
	}

	/** Review focus 1: a double click meets the raised version, a reload and retry the used mark. */
	@Test
	void mark_repeat_409() throws Exception {
		Ready ready = passed();
		String id = ready.checklistId();
		Map<String, Object> firstBody = body(id);
		ok(requalify(manager, id, firstBody));

		assertError(requalify(manager, id, firstBody), 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		assertError(requalify(manager, id), 409, "SURVEY_NOT_ALLOWED", "checklist");

		assertEquals(1, count(MARKED, ready.leadId()));
		assertEquals(1, count("select count(*) from survey_checklist_requalifications where checklist_id = ?", id));
	}

	@Test
	void mark_staleVersion_409() throws Exception {
		Ready ready = passed();
		Map<String, Object> body = body(ready.checklistId());
		body.put("expectedVersion", review.version(ready.checklistId()) - 1);

		assertError(requalify(manager, ready.checklistId(), body), 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		assertUnmarked(ready);
	}

	@Test
	void mark_unknownChecklist_404() throws Exception {
		Result answer = requalify(manager, checklists.newId(), body(1L));

		assertError(answer, 404, "NOT_FOUND", null);
	}

	// ==================== who (spec 7, S-4, D5-2) ====================

	@Test
	void mark_roleMatrix() throws Exception {
		for (TestUser allowed : List.of(leader, manager, admin, superAdmin)) {
			ok(requalify(allowed, passed().checklistId()));
		}
		Ready ready = passed();
		for (TestUser refused : List.of(sale, otherSale, outsiders.get(0), outsiders.get(1), outsiders.get(2))) {
			assertError(requalify(refused, ready.checklistId()), 403, "FORBIDDEN", null);
		}
		assertUnmarked(ready);
	}

	/** spec 5.3: the role comes before the body, also for a body the server cannot even read. */
	@Test
	void mark_refusedRole_badBody_403() throws Exception {
		Ready ready = passed();
		Map<String, Object> unknownKey = body(ready.checklistId());
		unknownKey.put("markedBy", manager.userId());
		Map<String, Object> unknownKind = body(ready.checklistId());
		unknownKind.put("reasonKind", "CHANGED_MIND");
		for (TestUser refused : List.of(sale, otherSale, outsiders.get(0), outsiders.get(1), outsiders.get(2))) {
			for (Map<String, Object> body : List.of(Map.<String, Object>of(), unknownKey, unknownKind)) {
				assertError(requalify(refused, ready.checklistId(), body), 403, "FORBIDDEN", null);
			}
		}
		assertUnmarked(ready);
	}

	@Test
	void mark_withoutToken_401() throws Exception {
		Ready ready = passed();

		assertError(requalify(null, ready.checklistId()), 401, "UNAUTHORIZED", null);
	}

	/** D5-2: a mark approves nothing, so a leader who reviewed or is the assignee may mark. */
	@Test
	void mark_leaderWhoIsTheAssignee_allowed() throws Exception {
		Ready ready = review.ready(leader, leader);
		review.submitted(leader, ready.checklistId());
		review.passed(manager, ready.checklistId());

		ok(requalify(leader, ready.checklistId()));
	}

	// ==================== the body (spec A-6, 5.3, S-9) ====================

	@Test
	void mark_phoneInReason_400() throws Exception {
		Ready ready = passed();
		for (String field : List.of("reason", "eventSource", "scope")) {
			Map<String, Object> body = body(ready.checklistId());
			body.put(field, "Goi 0905123456 de xac nhan");

			assertError(requalify(manager, ready.checklistId(), body), 400, "VALIDATION_FAILED", field);
		}
		assertUnmarked(ready);
	}

	@Test
	void mark_nul_400() throws Exception {
		Ready ready = passed();
		for (String field : List.of("reason", "eventSource", "scope")) {
			Map<String, Object> body = body(ready.checklistId());
			body.put(field, "Co ky tu\u0000 NUL");

			Result refused = requalify(manager, ready.checklistId(), body);

			assertError(refused, 400, "VALIDATION_FAILED", field);
			assertFalse(refused.rawBody().contains("Co ky tu"), refused.rawBody());
		}
		assertUnmarked(ready);
	}

	@Test
	void mark_textLimits_400() throws Exception {
		Ready ready = passed();
		for (Map.Entry<String, Object> wrong : Map.<String, Object>of("reason", "a".repeat(2001), "eventSource",
				"a".repeat(501), "scope", "a".repeat(501)).entrySet()) {
			Map<String, Object> body = body(ready.checklistId());
			body.put(wrong.getKey(), wrong.getValue());

			assertError(requalify(manager, ready.checklistId(), body), 400, "VALIDATION_FAILED", wrong.getKey());
		}
		for (String field : List.of("reason", "eventSource", "scope")) {
			Map<String, Object> body = body(ready.checklistId());
			body.put(field, "   ");

			assertError(requalify(manager, ready.checklistId(), body), 400, "VALIDATION_FAILED", field);
		}
		assertUnmarked(ready);
	}

	@Test
	void mark_textAtItsLimit_stored() throws Exception {
		Ready ready = passed();
		Map<String, Object> body = body(ready.checklistId());
		body.put("reason", "a".repeat(2000));
		body.put("eventSource", "b".repeat(500));
		body.put("scope", "c".repeat(500));

		ok(requalify(manager, ready.checklistId(), body));

		assertEquals("a".repeat(2000), requalification(ready.checklistId()).get("reason"));
	}

	@Test
	void mark_missingOrUnknownKey_400() throws Exception {
		Ready ready = passed();
		for (String field : List.of("expectedVersion", "reasonKind", "reason", "eventSource", "scope", "basisDate")) {
			Map<String, Object> missing = body(ready.checklistId());
			missing.remove(field);

			assertError(requalify(manager, ready.checklistId(), missing), 400, "VALIDATION_FAILED", field);

			Map<String, Object> empty = body(ready.checklistId());
			empty.put(field, null);

			assertError(requalify(manager, ready.checklistId(), empty), 400, "VALIDATION_FAILED", field);
		}
		Map<String, Object> unknown = body(ready.checklistId());
		unknown.put("markedBy", manager.userId());

		assertError(requalify(manager, ready.checklistId(), unknown), 400, "VALIDATION_FAILED", "markedBy");
		assertUnmarked(ready);
	}

	@Test
	void mark_unknownKind_400() throws Exception {
		Ready ready = passed();
		Map<String, Object> body = body(ready.checklistId());
		body.put("reasonKind", "CHANGED_MIND");

		assertError(requalify(manager, ready.checklistId(), body), 400, "VALIDATION_FAILED", "reasonKind");
		assertUnmarked(ready);
	}

	@Test
	void mark_basisDateBeforeDecision_400() throws Exception {
		Ready ready = passed();
		Map<String, Object> body = body(ready.checklistId());
		body.put("basisDate", today().minusDays(1).toString());

		assertError(requalify(manager, ready.checklistId(), body), 400, "VALIDATION_FAILED", "basisDate");
		assertUnmarked(ready);
	}

	@Test
	void mark_basisDateAfterToday_400() throws Exception {
		Ready ready = passed();
		Map<String, Object> body = body(ready.checklistId());
		body.put("basisDate", today().plusDays(1).toString());

		assertError(requalify(manager, ready.checklistId(), body), 400, "VALIDATION_FAILED", "basisDate");
		assertUnmarked(ready);
	}

	// ==================== one transaction (spec 5.3, S-2) ====================

	/** The access log row is the last write: a failure there leaves nothing of the mark. */
	@Test
	void mark_failureAfterMark_rollsBackAll() throws Exception {
		Ready ready = passed();
		long version = survey.version(ready.leadId());
		int accessLogs = survey.accessLogRows(ready.leadId());
		jdbc.execute("create function fail_requalification_access_log() returns trigger language plpgsql as"
				+ " $$ begin raise exception 'Test double: the access log insert failed.'; end $$");
		jdbc.execute("create trigger fail_requalification_access_log before insert on survey_access_logs for each row"
				+ " when (new.lead_id = '" + ready.leadId() + "') execute function fail_requalification_access_log()");
		try {
			assertEquals(500, requalify(manager, ready.checklistId()).status());
		} finally {
			jdbc.execute("drop trigger fail_requalification_access_log on survey_access_logs");
			jdbc.execute("drop function fail_requalification_access_log()");
		}

		assertUnmarked(ready);
		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
		assertEquals(version, survey.version(ready.leadId()));
		assertEquals(accessLogs, survey.accessLogRows(ready.leadId()));
	}

	// ==================== privacy (spec S-9, survey BR-4) ====================

	/** The one insert that carries the text fails: neither the answer nor a log line may repeat it. */
	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void mark_failureOnTheTextRow_keepsTheTextOutOfTheAnswerAndLogs(CapturedOutput output) throws Exception {
		Ready ready = passed();
		jdbc.execute("create function fail_requalification_row() returns trigger language plpgsql as"
				+ " $$ begin raise exception 'Test double: the row was refused.' using detail = new.reason; end $$");
		jdbc.execute("create trigger fail_requalification_row before insert on survey_checklist_requalifications for each"
				+ " row when (new.checklist_id = '" + ready.checklistId() + "') execute function"
				+ " fail_requalification_row()");
		Result failed;
		try {
			failed = requalify(manager, ready.checklistId());
		} finally {
			jdbc.execute("drop trigger fail_requalification_row on survey_checklist_requalifications");
			jdbc.execute("drop function fail_requalification_row()");
		}

		assertEquals(500, failed.status(), failed.rawBody());
		assertFalse(failed.rawBody().contains(SECRET_REASON), failed.rawBody());
		// the driver's error did reach a log line, so its absence of the text proves logServerErrorDetail holds
		assertTrue(output.getAll().contains("Test double: the row was refused."), "no logger saw the error");
		assertFalse(output.getAll().contains(SECRET_REASON));
		assertUnmarked(ready);
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void mark_textNeverInHistoryLogsOrErrors(CapturedOutput output) throws Exception {
		Ready ready = passed();
		Map<String, Object> phone = body(ready.checklistId());
		phone.put("reason", SECRET_REASON + " 0905123456");
		Result refused = requalify(manager, ready.checklistId(), phone);

		ok(requalify(manager, ready.checklistId()));

		Result block = block(leader, ready.leadId());
		Result lead = survey.api().get(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId());
		Result queue = survey.api().get(leader, SurveyApiTestSupport.LEADS);
		for (String secret : List.of(SECRET_REASON, SECRET_SOURCE, SECRET_SCOPE)) {
			assertEquals(0, jdbc.queryForObject("select count(*) from owner_lead_activities a where a.lead_id = ?"
					+ " and a::text like ?", Integer.class, ready.leadId(), "%" + secret + "%"));
			assertFalse(refused.rawBody().contains(secret), secret);
			assertFalse(block.rawBody().contains(secret), secret);
			assertFalse(lead.rawBody().contains(secret), secret);
			assertFalse(queue.rawBody().contains(secret), secret);
			assertFalse(output.getAll().contains(secret), secret);
		}
	}

	// ==================== reads (spec 4, 7, S-6, S-12) ====================

	@Test
	void read_markedRevision_unchanged_reasonRequalified() throws Exception {
		Ready ready = passed();
		String id = ready.checklistId();
		JsonNode before = checklists.readOk(leader, id).body();
		String hash = text("select content_sha256 from survey_checklist_submissions where checklist_id = ?", id);
		long evidence = count("select count(*) from survey_checklist_evidence where checklist_id = ?", id);

		ok(requalify(manager, id));

		JsonNode after = checklists.readOk(leader, id).body();
		for (String part : List.of("status", "groups", "submission", "decision", "earlierHazards", "effectiveTerms")) {
			assertEquals(before.path(part), after.path(part), part);
		}
		assertEquals(before.path("passed").path("surveyedScope"), after.path("passed").path("surveyedScope"));
		assertFalse(after.path("passed").path("usable").asBoolean());
		assertEquals("REQUALIFIED", after.path("passed").path("reason").asString());
		assertFalse(after.path("editable").asBoolean());
		assertEquals(hash, text("select content_sha256 from survey_checklist_submissions where checklist_id = ?", id));
		assertEquals(evidence, count("select count(*) from survey_checklist_evidence where checklist_id = ?", id));
	}

	@Test
	void read_requalificationForLeadViewers() throws Exception {
		Ready ready = passed();
		assertTrue(checklists.readOk(sale, ready.checklistId()).body().path("requalification").isNull());
		ok(requalify(manager, ready.checklistId()));

		JsonNode mark = checklists.readOk(sale, ready.checklistId()).body().path("requalification");

		assertEquals("WRONG_ASSET", mark.path("reasonKind").asString());
		assertEquals(SECRET_REASON, mark.path("reason").asString());
		assertEquals(SECRET_SOURCE, mark.path("eventSource").asString());
		assertEquals(SECRET_SCOPE, mark.path("scope").asString());
		assertEquals(today().toString(), mark.path("basisDate").asString());
		assertEquals(manager.userId(), mark.path("markedBy").path("userId").asString());
		assertEquals("Requalify Manager", mark.path("markedBy").path("name").asString());
		assertFalse(mark.path("markedAt").asString().isEmpty());
		assertEquals(404, checklists.read(otherSale, ready.checklistId()).status());
	}

	@Test
	void block_requalification_byRoleAndStatus() throws Exception {
		Ready ready = review.ready(leader, sale);
		String id = ready.checklistId();
		assertBlocked(block(manager, ready.leadId()));
		review.submitted(sale, id);
		assertBlocked(block(manager, ready.leadId()));
		review.passed(leader, id);

		for (TestUser allowed : List.of(leader, manager, admin, superAdmin)) {
			JsonNode action = block(allowed, ready.leadId()).body().path("survey").path("requalification");
			assertTrue(action.path("allowed").asBoolean(), action.toString());
			assertEquals(id, action.path("checklistId").asString());
			assertEquals(review.version(id), action.path("expectedVersion").asLong());
		}
		assertBlocked(block(sale, ready.leadId()));
		hold(ready.leadId());
		assertTrue(block(manager, ready.leadId()).body().path("survey").path("requalification").path("allowed")
				.asBoolean());

		ok(requalify(manager, id));

		assertBlocked(block(manager, ready.leadId()));
		JsonNode summary = block(manager, ready.leadId()).body().path("appointments").path(0).path("checklists").path(0);
		assertEquals(id, summary.path("id").asString());
		assertTrue(summary.path("requalified").asBoolean(), summary.toString());
		assertFalse(block(manager, ready.leadId()).body().path("survey").path("hasPassed").asBoolean());
	}

	@Test
	void block_requalification_closedLead_notAllowed() throws Exception {
		Ready ready = passed();
		ok(toStatus(ready.leadId(), "WITHDRAWN"));

		assertBlocked(block(manager, ready.leadId()));
	}

	// ==================== the lead's other commands (spec 4, D5-2, S-3) ====================

	@Test
	void onHold_afterMark_manualContacted() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());
		assertEquals(409, toStatus(ready.leadId(), "CONTACTED").status());

		ok(requalify(manager, ready.checklistId()));

		ok(toStatus(ready.leadId(), "CONTACTED"));
		assertEquals("CONTACTED", survey.leadStatus(ready.leadId()));
	}

	@Test
	void onHold_afterMark_resumeCannotReachQualified() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());
		ok(requalify(manager, ready.checklistId()));

		assertError(resume(ready.leadId()), 409, "SURVEY_NOT_ALLOWED", "checklist");

		assertEquals("ON_HOLD", survey.leadStatus(ready.leadId()));
		assertFalse(block(manager, ready.leadId()).body().path("resumeAllowed").asBoolean());
	}

	@Test
	void requirementChange_afterMark_raisesCycle() throws Exception {
		Ready ready = passed();
		assertEquals(409, changeIntent(ready.leadId()).status());
		ok(requalify(manager, ready.checklistId()));

		ok(changeIntent(ready.leadId()));

		assertEquals(2, count("select intent_cycle from owner_leads where id = ?", ready.leadId()));
		JsonNode passed = checklists.readOk(leader, ready.checklistId()).body().path("passed");
		assertEquals("REQUALIFIED", passed.path("reason").asString(), passed.toString());
	}

	// ==================== one current PASSED (spec I-1, S-1) ====================

	/** A second PASSED is reached by clearing the mark with SQL: only the index can refuse it. */
	@Test
	void decide_secondPassed_409() throws Exception {
		Ready first = passed();
		ok(requalify(manager, first.checklistId()));
		Visit second = visitAgain(first.leadId());
		String next = checklists.started(sale, second.appointmentId());
		String space = checklists.newId();
		review.fill(sale, next, space);
		review.photos(sale, next, space);
		review.submitted(sale, next);
		jdbc.update("update survey_checklists set requalified_at = null where id = ?", first.checklistId());

		assertError(review.decide(manager, next, "PASSED"), 409, "CONCURRENT_MODIFICATION", "expectedVersion");

		assertEquals("SUBMITTED", text("select status from survey_checklists where id = ?", next));
		assertEquals(0, count("select count(*) from survey_checklist_decisions where checklist_id = ?", next));
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(first.leadId()));
	}

	// ==================== helpers ====================

	private Ready passed() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		review.passed(leader, ready.checklistId());
		return ready;
	}

	private static LocalDate today() {
		return LocalDate.now(SurveyTimeRules.ZONE);
	}

	private Map<String, Object> body(String checklistId) {
		return body(review.version(checklistId));
	}

	private static Map<String, Object> body(Long version) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", version);
		body.put("reasonKind", "WRONG_ASSET");
		body.put("reason", SECRET_REASON);
		body.put("eventSource", SECRET_SOURCE);
		body.put("scope", SECRET_SCOPE);
		body.put("basisDate", today().toString());
		return body;
	}

	private Result requalify(TestUser caller, String checklistId) throws Exception {
		return requalify(caller, checklistId, body(checklistId));
	}

	private Result requalify(TestUser caller, String checklistId, Map<String, Object> body) throws Exception {
		return checklists.api().post(caller, ChecklistApiTestSupport.CHECKLISTS + "/" + checklistId + "/requalification",
				body);
	}

	private Result block(TestUser viewer, String leadId) throws Exception {
		Result block = checklists.api().get(viewer, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");
		ok(block);
		return block;
	}

	private static void assertBlocked(Result block) {
		JsonNode action = block.body().path("survey").path("requalification");
		assertFalse(action.isMissingNode(), block.rawBody());
		assertFalse(action.path("allowed").asBoolean(), action.toString());
		assertTrue(action.path("checklistId").isNull(), action.toString());
		assertTrue(action.path("expectedVersion").isNull(), action.toString());
	}

	private void assertUnmarked(Ready ready) {
		assertNull(requalifiedAt(ready.checklistId()));
		assertEquals(0, count("select count(*) from survey_checklist_requalifications where checklist_id = ?",
				ready.checklistId()));
		assertEquals(0, count(MARKED, ready.leadId()));
	}

	private Instant requalifiedAt(String checklistId) {
		return jdbc.queryForObject("select requalified_at from survey_checklists where id = ?", Instant.class,
				checklistId);
	}

	private Map<String, Object> requalification(String checklistId) {
		return jdbc.queryForMap("select * from survey_checklist_requalifications where checklist_id = ?", checklistId);
	}

	private long count(String sql, String id) {
		return jdbc.queryForObject(sql, Long.class, id);
	}

	private String text(String sql, String id) {
		return jdbc.queryForObject(sql, String.class, id);
	}

	private void hold(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "ON_HOLD");
		body.put("expectedVersion", survey.version(leadId));
		body.put("note", "Owner abroad");
		ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/status-changes", body));
	}

	private Result toStatus(String leadId, String status) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", status);
		body.put("expectedVersion", survey.version(leadId));
		if ("WITHDRAWN".equals(status)) {
			body.put("reason", "Owner sold the house");
		}
		return checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/status-changes", body);
	}

	private Result resume(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(leadId));
		body.put("reason", "Owner is back");
		body.put("basis", ChecklistApiTestSupport.basis(today(), "Owner called"));
		return checklists.api().post(sale, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-resumptions", body);
	}

	private Result changeIntent(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", survey.version(leadId));
		body.put("newIntent", "SHORT_TERM_RENT");
		body.put("askingPrice", null);
		body.put("reason", "Owner changed plans");
		body.put("basis", ChecklistApiTestSupport.basis(today(), "Owner called"));
		return checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes", body);
	}

	private Visit visitAgain(String leadId) throws Exception {
		Instant start = SurveyApiTestSupport.startIn(24);
		Map<String, Object> booking = new LinkedHashMap<>(survey.booking(leadId, start));
		booking.put("resurveyReason", "The requalification asks for a new visit");
		Result booked = survey.book(sale, leadId, booking);
		assertEquals(201, booked.status(), booked.rawBody());
		String appointmentId = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		Instant arrivedAt = start.minus(java.time.Duration.ofDays(2).plusHours(1));
		survey.shiftBack(appointmentId, java.time.Duration.ofDays(2).plusHours(1));
		ok(checklists.recordVisit(sale, leadId, appointmentId, arrivedAt));
		return new Visit(leadId, appointmentId, arrivedAt);
	}
}
