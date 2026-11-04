package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;

/**
 * POST /survey-checklists/{id}/decision (survey-checklist submission spec 4.2, 4.5, 5.5, S-5..S-9): the outcome table,
 * the PASSED rules, who may decide and what stays out of the lead. Needs Docker.
 */
class ChecklistDecisionApiPostgresTest extends MediaApiTestBase {

	private static final String SECRET_REASON = "Ly do rieng cua nguoi duyet";

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
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0967");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Decide Sale", "SALE");
		leader = survey.user("Decide Leader", "SALE_LEADER");
		manager = survey.user("Decide Manager", "SALES_MANAGER");
		admin = survey.user("Decide Admin", "ADMIN");
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

	private Ready submitted() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		return ready;
	}

	private static Map<String, Object> body(long version, String outcome) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", version);
		body.put("outcome", outcome);
		body.put("rejectedEvidence", List.of());
		return body;
	}

	private Result decide(TestUser caller, String checklistId, Map<String, Object> body) throws Exception {
		return checklists.api().post(caller, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId + "/decision",
				body);
	}

	private Result passed(TestUser caller, String checklistId) throws Exception {
		return decide(caller, checklistId, body(review.version(checklistId), "PASSED"));
	}

	private Result needsChanges(TestUser caller, String checklistId, String kind) throws Exception {
		Map<String, Object> body = body(review.version(checklistId), "NEEDS_CHANGES");
		body.put("needsChangesKind", kind);
		body.put("reason", SECRET_REASON);
		return decide(caller, checklistId, body);
	}

	private Result failed(TestUser caller, String checklistId, String kind) throws Exception {
		Map<String, Object> body = body(review.version(checklistId), "FAILED");
		body.put("failedKind", kind);
		body.put("reason", SECRET_REASON);
		return decide(caller, checklistId, body);
	}

	private static List<String> fields(Result result) {
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		return fields;
	}

	private static void assertRefused(Result refused, int status, String code, String... fields) {
		assertEquals(status, refused.status(), refused.rawBody());
		assertEquals(code, refused.body().path("code").asString(), refused.rawBody());
		assertEquals(List.of(fields), fields(refused), refused.rawBody());
	}

	private Map<String, Object> reviewedRow(String leadId) {
		return jdbc.queryForMap("select actor_user_id, from_status, to_status, reference_id, reference_no,"
				+ " reference_detail, note from owner_lead_activities where lead_id = ? and type = 'CHECKLIST_REVIEWED'",
				leadId);
	}

	// ==================== the outcome table (spec 4.5, S-6, S-8) ====================

	@Test
	void decide_passed_qualifiesTheLeadInOneTransaction() throws Exception {
		Ready ready = submitted();
		long leadVersion = survey.version(ready.leadId());

		Result answer = passed(leader, ready.checklistId());

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		assertEquals("PASSED", body.path("checklist").path("status").asString());
		assertEquals("QUALIFIED", body.path("lead").path("status").asString());
		assertEquals(leadVersion + 1, body.path("lead").path("version").asLong());
		assertEquals("CHECKLIST_REVIEWED", body.path("historyEntry").path("type").asString());
		assertEquals("PASSED", body.path("historyEntry").path("referenceDetail").asString());
		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
		Map<String, Object> row = reviewedRow(ready.leadId());
		assertEquals(leader.userId(), row.get("actor_user_id"));
		assertEquals("SURVEY_SCHEDULED", row.get("from_status"));
		assertEquals("QUALIFIED", row.get("to_status"));
		assertEquals(ready.checklistId(), row.get("reference_id"));
	}

	@Test
	void decide_documentsOnly_keepsTheLeadScheduled() throws Exception {
		Ready ready = submitted();

		assertEquals(200, needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY").status());

		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(ready.leadId()));
		Map<String, Object> row = reviewedRow(ready.leadId());
		assertNull(row.get("to_status"));
		assertEquals("NEEDS_CHANGES:DOCUMENTS_ONLY", row.get("reference_detail"));
	}

	@Test
	void decide_resurveyRequired_movesToContacted() throws Exception {
		Ready ready = submitted();

		assertEquals(200, needsChanges(leader, ready.checklistId(), "RESURVEY_REQUIRED").status());

		assertEquals("CONTACTED", survey.leadStatus(ready.leadId()));
		assertEquals("NEEDS_CHANGES:RESURVEY_REQUIRED", reviewedRow(ready.leadId()).get("reference_detail"));
	}

	@Test
	void decide_failedTemporary_movesToContacted() throws Exception {
		Ready ready = submitted();

		assertEquals(200, failed(manager, ready.checklistId(), "TEMPORARY").status());

		assertEquals("CONTACTED", survey.leadStatus(ready.leadId()));
		assertEquals("FAILED:TEMPORARY", reviewedRow(ready.leadId()).get("reference_detail"));
	}

	@Test
	void decide_failedOutOfScope_closesWithTheFixedReason() throws Exception {
		Ready ready = submitted();

		assertEquals(200, failed(admin, ready.checklistId(), "OUT_OF_SCOPE").status());

		Map<String, Object> lead = jdbc.queryForMap("select status, close_reason, next_action_at from owner_leads"
				+ " where id = ?", ready.leadId());
		assertEquals("REJECTED", lead.get("status"));
		assertEquals("Survey failed: out of scope (checklist #1)", lead.get("close_reason"));
		assertNull(lead.get("next_action_at"));
	}

	@Test
	void decide_failedNotAccepted_closesWithTheFixedReason() throws Exception {
		Ready ready = submitted();

		assertEquals(200, failed(leader, ready.checklistId(), "NOT_ACCEPTED").status());

		assertEquals("Survey failed: not accepted (checklist #1)", jdbc.queryForObject("select close_reason from"
				+ " owner_leads where id = ?", String.class, ready.leadId()));
	}

	/** survey BR-4, BR-14, spec S-8: the reviewer's words stay in the decision. */
	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void decide_reviewTextNeverInHistoryCloseReasonOrLogs(CapturedOutput output) throws Exception {
		Ready ready = submitted();

		assertEquals(200, failed(leader, ready.checklistId(), "OUT_OF_SCOPE").status());

		assertEquals(SECRET_REASON, jdbc.queryForObject("select reason from survey_checklist_decisions"
				+ " where checklist_id = ?", String.class, ready.checklistId()));
		assertNull(reviewedRow(ready.leadId()).get("note"));
		assertFalse(jdbc.queryForObject("select close_reason from owner_leads where id = ?", String.class,
				ready.leadId()).contains(SECRET_REASON));
		assertEquals(0, jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?"
				+ " and (note like ? or reference_detail like ?)", Integer.class, ready.leadId(), "%Ly do%", "%Ly do%"));
		assertFalse(output.getAll().contains(SECRET_REASON));
	}

	// ==================== the PASSED rules (spec 4.5, S-7) ====================

	@Test
	void decide_openHazard_refusesPassedOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		checklists.saved(sale, ready.checklistId(), "safety", c -> ChecklistApiTestSupport.set(c, "hazards",
				checklists.value(List.of(checklists.hazard(checklists.newId(), "IMPORTANT", "OPEN", null)))));
		review.submitted(sale, ready.checklistId());

		assertRefused(passed(leader, ready.checklistId()), 409, "CHECKLIST_DECISION_NOT_ALLOWED", "hazards");
		assertEquals(200, needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY").status());
	}

	@Test
	void decide_rejectedPhoto_refusesPassedAndCoverage() throws Exception {
		Ready ready = submitted();
		Map<String, Object> body = body(review.version(ready.checklistId()), "PASSED");
		body.put("rejectedEvidence", List.of(Map.of("mediaId", ready.photos().get(0), "reason", "Blurred")));

		assertRefused(decide(leader, ready.checklistId(), body), 409, "CHECKLIST_DECISION_NOT_ALLOWED",
				"evidence.rejected", "evidence.coverage");
	}

	@Test
	void decide_heldPhoto_refusesPassed_allowsNeedsChanges() throws Exception {
		Ready ready = submitted();
		media.held(ready.photos().get(0), admin.userId(), "SECURITY_INCIDENT");

		Result refused = passed(leader, ready.checklistId());

		assertEquals(409, refused.status(), refused.rawBody());
		assertTrue(fields(refused).contains("evidence.unavailable"), refused.rawBody());
		assertEquals(200, needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY").status());
	}

	@Test
	void decide_marksAreStored() throws Exception {
		Ready ready = submitted();
		Map<String, Object> body = body(review.version(ready.checklistId()), "NEEDS_CHANGES");
		body.put("needsChangesKind", "DOCUMENTS_ONLY");
		body.put("reason", "Bring the title deed");
		body.put("rejectedEvidence", List.of(Map.of("mediaId", ready.photos().get(1), "reason", "Blurred")));

		assertEquals(200, decide(leader, ready.checklistId(), body).status());

		assertEquals("Blurred", jdbc.queryForObject("select reason from survey_checklist_evidence_rejections"
				+ " where checklist_id = ? and media_id = ?", String.class, ready.checklistId(), ready.photos().get(1)));
	}

	// ==================== who may decide (spec 4.2, S-5) ====================

	@Test
	void decision_saleRole_403BeforeLookup() throws Exception {
		assertEquals(403, decide(sale, "01JNOSUCHCHECKLIST00000000", body(0, "PASSED")).status());
	}

	@Test
	void decide_withoutToken_401() throws Exception {
		assertEquals(401, decide(null, "01JNOSUCHCHECKLIST00000000", body(0, "PASSED")).status());
	}

	@Test
	void decide_unknownChecklist_404() throws Exception {
		assertEquals(404, decide(leader, "01JNOSUCHCHECKLIST00000000", body(0, "PASSED")).status());
	}

	@Test
	void decide_assigneeLeader_403AndEvent() throws Exception {
		Ready ready = submitted();
		SurveyApiTestSupport.ok(checklists.api().post(manager, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/assign", Map.of("assigneeUserId", leader.userId(), "expectedVersion",
				survey.version(ready.leadId()))));

		assertRefused(passed(leader, ready.checklistId()), 403, "CHECKLIST_REVIEW_NOT_ALLOWED", "reviewer");

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ?"
				+ " and action = 'REVIEW' and outcome = 'NOT_INDEPENDENT'", Integer.class, ready.leadId()));
		assertEquals(200, passed(manager, ready.checklistId()).status(), "someone else still can");
	}

	/** spec 5.5: the state is answered first, so an excluded user of a held lead hears about the lead. */
	@Test
	void decide_excluded_403AndEvent_afterStateChecks() throws Exception {
		Ready ready = submitted();
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/status-changes", holdBody(ready.leadId())));
		SurveyApiTestSupport.ok(checklists.api().post(manager, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/assign", Map.of("assigneeUserId", leader.userId(), "expectedVersion",
				survey.version(ready.leadId()))));

		assertRefused(passed(leader, ready.checklistId()), 409, "CHECKLIST_DECISION_NOT_ALLOWED", "lead.status");

		assertEquals(0, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ?"
				+ " and outcome = 'NOT_INDEPENDENT'", Integer.class, ready.leadId()));
	}

	private Map<String, Object> holdBody(String leadId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "ON_HOLD");
		body.put("expectedVersion", survey.version(leadId));
		body.put("note", "Owner abroad");
		return body;
	}

	// ==================== state (spec 5.5, S-9, S-17) ====================

	@Test
	void decide_onHoldLead_409LeadStatus() throws Exception {
		Ready ready = submitted();
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/status-changes", holdBody(ready.leadId())));

		assertRefused(passed(manager, ready.checklistId()), 409, "CHECKLIST_DECISION_NOT_ALLOWED", "lead.status");
	}

	@Test
	void decide_servingOff_409() throws Exception {
		Ready ready = submitted();
		switches.serving(false);

		assertRefused(passed(leader, ready.checklistId()), 409, "CHECKLIST_DECISION_NOT_ALLOWED", "media.serving");
	}

	@Test
	void decide_secondTime_409() throws Exception {
		Ready ready = submitted();
		assertEquals(200, needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY").status());

		assertRefused(passed(manager, ready.checklistId()), 409, "CHECKLIST_DECISION_NOT_ALLOWED", "status");
	}

	@Test
	void decide_staleVersion_409() throws Exception {
		Ready ready = submitted();

		assertRefused(decide(leader, ready.checklistId(), body(review.version(ready.checklistId()) + 5, "PASSED")),
				409, "CONCURRENT_MODIFICATION", "expectedVersion");
	}

	@Test
	void decide_body_400() throws Exception {
		Ready ready = submitted();
		Map<String, Object> body = body(review.version(ready.checklistId()), "NEEDS_CHANGES");
		body.put("needsChangesKind", "DOCUMENTS_ONLY");

		assertRefused(decide(leader, ready.checklistId(), body), 400, "VALIDATION_FAILED", "reason");
	}
}
