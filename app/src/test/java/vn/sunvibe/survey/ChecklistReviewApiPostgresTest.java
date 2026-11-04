package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.core.application.service.MediaContentDeletionJob;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * The review contract over HTTP (survey-checklist submission spec 7.1..7.3): what the checklist read and the survey
 * block answer at each stage, the deselection endpoint, and the error bodies of the new codes. The rules themselves have
 * their own API tests per command. Needs Docker.
 */
class ChecklistReviewApiPostgresTest extends MediaApiTestBase {

	private static final String CHECKLISTS = ChecklistReviewApiSupport.CHECKLISTS;

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

	@Autowired
	private MediaContentDeletionJob deletion;

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
		survey = new SurveyApiTestSupport(api, jdbc, "0962");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Review Sale", "SALE");
		leader = survey.user("Review Leader", "SALE_LEADER");
		manager = survey.user("Review Manager", "SALES_MANAGER");
		admin = survey.user("Review Admin", "ADMIN");
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

	private JsonNode read(TestUser viewer, String checklistId) throws Exception {
		return checklists.readOk(viewer, checklistId).body();
	}

	private JsonNode block(TestUser viewer, String leadId) throws Exception {
		Result answer = checklists.api().get(viewer, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");
		SurveyApiTestSupport.ok(answer);
		return answer.body();
	}

	private String revised(TestUser caller, String checklistId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", null);
		Result answer = checklists.api().post(caller, CHECKLISTS + "/" + checklistId + "/revisions", body);
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("checklist").path("id").asString();
	}

	private void hold(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "ON_HOLD");
		body.put("expectedVersion", survey.version(leadId));
		body.put("note", "Owner abroad");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId
				+ "/status-changes", body));
	}

	private Result deselect(TestUser caller, String checklistId, String mediaId, Map<String, Object> body)
			throws Exception {
		return checklists.api().post(caller, CHECKLISTS + "/" + checklistId + "/media/" + mediaId + "/deselection",
				body);
	}

	private static Map<String, Object> noCorrection() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("correction", null);
		return body;
	}

	private static List<String> ids(JsonNode media) {
		List<String> ids = new ArrayList<>();
		media.path("items").forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	private static List<String> texts(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(value -> values.add(value.asString()));
		return values;
	}

	/** ADR-010: code, message, errors with the field of spec 7.3, and a correlation id. */
	private static void assertEnvelope(Result refused, int status, String code, String field) {
		assertEquals(status, refused.status(), refused.rawBody());
		assertEquals(code, refused.body().path("code").asString(), refused.rawBody());
		assertFalse(refused.body().path("message").asString().isBlank(), refused.rawBody());
		assertFalse(refused.body().path("correlationId").asString().isBlank(), refused.rawBody());
		assertEquals(field, refused.body().path("errors").path(0).path("field").asString(), refused.rawBody());
		assertTrue(refused.body().path("errors").path(0).path("allowedValues").isEmpty()
				|| refused.body().path("errors").path(0).path("allowedValues").isArray(), refused.rawBody());
	}

	// ==================== the checklist read (spec 7.2) ====================

	@Test
	void read_draft_hasTheEmptyReviewParts() throws Exception {
		Ready ready = review.ready(leader, sale);

		JsonNode checklist = read(manager, ready.checklistId());

		assertTrue(checklist.path("submission").isNull());
		assertTrue(checklist.path("decision").isNull());
		assertTrue(checklist.path("passed").isNull());
		assertTrue(checklist.path("previousChecklistId").isNull());
		assertFalse(checklist.path("review").path("canReview").asBoolean());
		assertEquals("NOT_SUBMITTED", checklist.path("review").path("reason").asString());
	}

	@Test
	void read_submitted_showsTheSubmissionToAnyReaderOfTheLead() throws Exception {
		Ready ready = submitted();

		JsonNode checklist = read(sale, ready.checklistId());

		assertEquals("SUBMITTED", checklist.path("status").asString());
		assertEquals("SUBMITTED", checklist.path("readOnlyReason").asString());
		JsonNode submission = checklist.path("submission");
		assertEquals(sale.userId(), submission.path("submittedBy").path("userId").asString());
		assertEquals("Review Sale", submission.path("submittedBy").path("name").asString());
		assertTrue(submission.hasNonNull("submittedAt"));
		assertEquals("MEDIA_V1_NO_PERMISSION", submission.path("mediaPolicy").asString());
		assertEquals(64, submission.path("contentSha256").asString().length());
		assertEquals(4, submission.path("evidence").size());
		JsonNode entry = submission.path("evidence").path(0);
		assertTrue(ready.photos().contains(entry.path("mediaId").asString()));
		assertEquals(64, entry.path("fullSha256").asString().length());
		assertTrue(entry.has("category") && entry.has("spaceId") && entry.has("spaceCode"), entry.toString());
		assertTrue(entry.path("accepted").isNull(), "nobody judged it yet");
		assertTrue(entry.path("rejectionReason").isNull());
		assertEquals("NOT_REVIEWER_ROLE", checklist.path("review").path("reason").asString());
		assertEquals(List.of(), texts(checklist.path("review").path("decisionBlockedBy")));
	}

	@Test
	void read_reviewer_canReview_withTheRulesThatFail() throws Exception {
		Ready ready = submitted();
		assertTrue(read(manager, ready.checklistId()).path("review").path("canReview").asBoolean());

		media.held(ready.photos().get(0), admin.userId(), "SECURITY_INCIDENT");

		JsonNode access = read(manager, ready.checklistId()).path("review");
		assertTrue(access.path("canReview").asBoolean(), "a held photo blocks PASSED only");
		assertEquals(List.of("evidence.unavailable", "evidence.coverage"), texts(access.path("decisionBlockedBy")));
	}

	@Test
	void read_decided_showsTheDecisionAndTheMarks() throws Exception {
		Ready ready = submitted();
		String rejected = ready.photos().get(0);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", review.version(ready.checklistId()));
		body.put("outcome", "NEEDS_CHANGES");
		body.put("needsChangesKind", "DOCUMENTS_ONLY");
		body.put("reason", "Bring the land paper");
		body.put("notes", "Keep the kitchen photos");
		body.put("rejectedEvidence", List.of(Map.of("mediaId", rejected, "reason", "Blurred")));
		SurveyApiTestSupport.ok(checklists.api().post(manager, CHECKLISTS + "/" + ready.checklistId() + "/decision",
				body));

		JsonNode checklist = read(sale, ready.checklistId());

		JsonNode decision = checklist.path("decision");
		assertEquals("NEEDS_CHANGES", decision.path("outcome").asString());
		assertEquals("DOCUMENTS_ONLY", decision.path("needsChangesKind").asString());
		assertTrue(decision.path("failedKind").isNull());
		assertEquals("Bring the land paper", decision.path("reason").asString());
		assertEquals("Keep the kitchen photos", decision.path("notes").asString());
		assertTrue(decision.path("limitations").isNull());
		assertEquals(manager.userId(), decision.path("reviewer").path("userId").asString());
		assertEquals("Review Manager", decision.path("reviewer").path("name").asString());
		assertTrue(decision.hasNonNull("decidedAt"));
		for (JsonNode entry : checklist.path("submission").path("evidence")) {
			boolean marked = rejected.equals(entry.path("mediaId").asString());
			assertEquals(!marked, entry.path("accepted").asBoolean());
			assertEquals(marked ? "Blurred" : null, entry.path("rejectionReason").isNull() ? null
					: entry.path("rejectionReason").asString());
		}
		assertEquals("DECIDED", checklist.path("readOnlyReason").asString());
		assertEquals("NOT_REVIEWER_ROLE", checklist.path("review").path("reason").asString());
		assertEquals("NOT_SUBMITTED", read(manager, ready.checklistId()).path("review").path("reason").asString());
	}

	/** S-6: the surveyed scope is G1's, on the read and on the block. */
	@Test
	void read_passed_usableWithTheScopeOfG1() throws Exception {
		Ready ready = submitted();
		review.passed(leader, ready.checklistId());

		JsonNode passed = read(sale, ready.checklistId()).path("passed");

		assertTrue(passed.path("usable").asBoolean());
		assertTrue(passed.path("reason").isNull());
		assertEquals("WHOLE", passed.path("surveyedScope").path("rentalScope").asString());
		assertEquals(ready.spaceId(), passed.path("surveyedScope").path("spaces").path(0).path("id").asString());
		assertEquals("A1", passed.path("surveyedScope").path("spaces").path(0).path("code").asString());
		assertEquals(passed, block(sale, ready.leadId()).path("survey").path("passed"));
	}

	@Test
	void read_passed_heldThenReleased() throws Exception {
		Ready ready = submitted();
		review.passed(leader, ready.checklistId());
		media.held(ready.photos().get(0), admin.userId(), "SECURITY_INCIDENT");

		JsonNode held = read(sale, ready.checklistId()).path("passed");
		assertFalse(held.path("usable").asBoolean());
		assertEquals("EVIDENCE_HELD", held.path("reason").asString());

		jdbc.update("update survey_media set hold_released_at = held_at + interval '1 second',"
				+ " hold_released_by_user_id = ? where id = ?", admin.userId(), ready.photos().get(0));

		assertTrue(read(sale, ready.checklistId()).path("passed").path("usable").asBoolean());
	}

	@Test
	void read_passed_closedLead_leadClosed() throws Exception {
		Ready ready = submitted();
		review.passed(leader, ready.checklistId());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "WITHDRAWN");
		body.put("expectedVersion", survey.version(ready.leadId()));
		body.put("reason", "Owner sold the house");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/status-changes", body));

		assertEquals("LEAD_CLOSED", read(leader, ready.checklistId()).path("passed").path("reason").asString());
	}

	@Test
	void read_revision_namesItsPrevious() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");

		String second = revised(sale, ready.checklistId());

		assertEquals(ready.checklistId(), read(sale, second).path("previousChecklistId").asString());
	}

	// ==================== the survey block (spec 7.2) ====================

	@Test
	void block_whileSubmitted() throws Exception {
		Ready ready = submitted();

		JsonNode block = block(sale, ready.leadId());

		assertFalse(block.path("booking").path("allowed").asBoolean());
		assertEquals("CHECKLIST_SUBMITTED", block.path("booking").path("blockedBy").asString());
		assertTrue(block.path("survey").path("hasSubmitted").asBoolean());
		assertFalse(block.path("survey").path("hasPassed").asBoolean());
		assertTrue(block.path("survey").path("passed").isNull());
		assertEquals("CHECKLIST_EXISTS", block.path("checklistStart").path("blockedBy").asString());
		assertFalse(block.path("revisionStart").path("allowed").asBoolean());
		assertEquals("NOT_RETURNED_FOR_DOCUMENTS", block.path("revisionStart").path("blockedBy").asString());
		JsonNode summary = block.path("appointments").path(0).path("checklists").path(0);
		assertEquals("SUBMITTED", summary.path("status").asString());
		assertTrue(summary.path("outcome").isNull());
		assertTrue(block.path("resume").path("target").isNull());
	}

	@Test
	void block_afterADocumentsReturn_offersTheRevision() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");

		JsonNode block = block(sale, ready.leadId());

		assertTrue(block.path("revisionStart").path("allowed").asBoolean());
		assertEquals(ready.checklistId(), block.path("revisionStart").path("checklistId").asString());
		assertFalse(block.path("revisionStart").path("intakeRequired").asBoolean());
		assertEquals("NEEDS_CHANGES", block.path("appointments").path(0).path("checklists").path(0).path("outcome")
				.asString());
	}

	@Test
	void block_heldWithPassed_resumesToQualified() throws Exception {
		Ready ready = submitted();
		review.passed(leader, ready.checklistId());
		hold(ready.leadId());

		JsonNode block = block(sale, ready.leadId());

		assertTrue(block.path("resumeAllowed").asBoolean());
		assertEquals("QUALIFIED", block.path("resume").path("target").asString());
		assertEquals("CHECKLIST_PASSED", block.path("booking").path("blockedBy").asString());
	}

	// ==================== decide: reviewer account (plan PD-6) ====================

	/**
	 * The token filter already refuses a locked account, so over HTTP this answers 401; the command's own 403 (PD-6) for
	 * an account locked after the filter is {@code SurveyChecklistReviewDecideTest}.
	 */
	@Test
	void decide_lockedReviewer_refusedNothingWritten() throws Exception {
		Ready ready = submitted();
		TestUser locked = survey.user("Review Locked", "SALE_LEADER");
		jdbc.update("update users set user_status = 'LOCK' where id = ?", locked.userId());

		Result refused = review.decide(locked, ready.checklistId(), "PASSED");

		assertEquals(401, refused.status(), refused.rawBody());
		assertEquals("SUBMITTED", jdbc.queryForObject("select status from survey_checklists where id = ?", String.class,
				ready.checklistId()));
	}

	/** S-5: who received an owner's statement touched nothing of the record's evidence. */
	@Test
	void ownerStatementReceiver_mayDecide() throws Exception {
		Ready ready = review.ready(leader, sale);
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);
		checklists.saved(sale, ready.checklistId(), "dimensions", c -> ChecklistApiTestSupport.set(c,
				"spaces.0.area", checklists.ownerStated(new java.math.BigDecimal("80.5"), today, leader.userId(),
						"Called the owner")));
		review.submitted(sale, ready.checklistId());

		assertTrue(read(leader, ready.checklistId()).path("review").path("canReview").asBoolean());
		SurveyApiTestSupport.ok(review.decide(leader, ready.checklistId(), "PASSED"));
	}

	/** S-11: a visit returned for a re-survey never gets a PASSED. */
	@Test
	void decide_returnedForResurvey_noPassedOnThatVisit() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "RESURVEY_REQUIRED");

		assertEnvelope(review.decide(manager, ready.checklistId(), "PASSED"), 409, "CHECKLIST_DECISION_NOT_ALLOWED",
				"status");
	}

	// ==================== S-4, S-12 ====================

	@Test
	void frozenPhoto_survivesACascadeInALaterRevisionAndTheDeletionJob() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
		String second = revised(sale, ready.checklistId());

		checklists.saved(sale, second, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(checklists.newId(), "A2"))));
		deletion.drain();

		for (String photo : ready.photos()) {
			Map<String, Object> row = media.row(photo);
			assertNull(row.get("removed_at"), photo);
			assertNull(row.get("content_deleted_at"), photo);
			assertEquals(200, media.read(sale, photo, "preview").status(), photo);
		}
	}

	/** S-12: the copied hazard is one warning, and a downgrade on the same visit lifts R2. */
	@Test
	void revision_sameVisitDowngrade_liftsTheHazardRule() throws Exception {
		Ready ready = review.ready(leader, sale);
		String hazard = checklists.newId();
		checklists.saved(sale, ready.checklistId(), "safety", c -> ChecklistApiTestSupport.set(c, "hazards",
				checklists.value(List.of(checklists.hazard(hazard, "IMPORTANT", "OPEN", null)))));
		review.submitted(sale, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
		String second = revised(sale, ready.checklistId());

		JsonNode copied = read(sale, second);
		assertEquals(1, copied.path("readiness").path("warnings").size(), copied.path("readiness").toString());
		assertEquals("OPEN_IMPORTANT_HAZARD", copied.path("readiness").path("warnings").path(0).path("type").asString());
		assertEquals(0, copied.path("earlierHazards").size());

		checklists.saved(sale, second, "safety", c -> ChecklistApiTestSupport.set(c, "hazards",
				checklists.value(List.of(checklists.hazard(hazard, "MINOR", "OPEN", null)))));
		review.submitted(sale, second);

		assertEquals(List.of(), texts(read(manager, second).path("review").path("decisionBlockedBy")));
		SurveyApiTestSupport.ok(review.decide(manager, second, "PASSED"));
	}

	/** Spec 4.6, U-5: a value copied into a revision keeps its correction on the read of that revision. */
	@Test
	void revision_copiedCorrection_stillResolvesOnTheRead() throws Exception {
		TestUser other = survey.user("Review Other Sale", "SALE");
		Ready ready = review.ready(leader, sale);
		checklists.reassign(leader, ready.leadId(), other);
		assertEquals(201, checklists.takeOver(other, ready.checklistId(), ready.visit()).status());
		checklists.saved(other, ready.checklistId(), "dimensions", ChecklistApiTestSupport.correction("Measured again",
				ready.visit().arrivalDate(), "Owner confirmed"), c -> ChecklistApiTestSupport.set(c, "spaces.0.area",
				checklists.sourced(new java.math.BigDecimal("81.5"), "MEASURED")));
		review.submitted(other, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");

		JsonNode copied = read(other, revised(other, ready.checklistId()));

		String saveId = copied.path("groups").path("dimensions").path("content").path("spaces").path(0).path("area")
				.path("meta").path("correctionSaveId").asString();
		assertFalse(saveId.isBlank(), copied.path("groups").path("dimensions").toString());
		List<String> resolved = new ArrayList<>();
		copied.path("corrections").forEach(correction -> resolved.add(correction.path("saveId").asString()));
		assertTrue(resolved.contains(saveId), resolved.toString());
		for (JsonNode correction : copied.path("corrections")) {
			if (saveId.equals(correction.path("saveId").asString())) {
				assertEquals("Review Other Sale", correction.path("actorName").asString());
				assertEquals("Measured again", correction.path("reason").asString());
			}
		}
		assertEquals(0, copied.path("editors").size(), "nobody saved this revision yet");
	}

	// ==================== deselection (spec 7.1, 5.5, S-13) ====================

	private String carriedInto(Ready ready) throws Exception {
		review.submitted(sale, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
		return revised(sale, ready.checklistId());
	}

	@Test
	void deselection_answersTheMediaListOfTheRevision() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);
		String photo = ready.photos().get(0);

		Result answer = deselect(sale, second, photo, noCorrection());

		SurveyApiTestSupport.ok(answer);
		assertFalse(ids(answer.body()).contains(photo));
		assertEquals(3, ids(answer.body()).size());
	}

	@Test
	void deselection_repeated_answersTheSameList() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);
		String photo = ready.photos().get(0);
		SurveyApiTestSupport.ok(deselect(sale, second, photo, noCorrection()));

		Result again = deselect(sale, second, photo, noCorrection());

		SurveyApiTestSupport.ok(again);
		assertEquals(3, ids(again.body()).size());
	}

	@Test
	void deselection_ownPhotoOfTheRevision_404() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);
		String own = review.photo(sale, second, null, null, "EXTERIOR_ACCESS");

		assertEquals(404, deselect(sale, second, own, noCorrection()).status());
	}

	@Test
	void deselection_afterSubmit_409ReadOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);
		review.submitted(sale, second);

		Result refused = deselect(sale, second, ready.photos().get(0), noCorrection());

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_READ_ONLY", refused.body().path("code").asString());
	}

	@Test
	void deselection_withoutTheKey_400() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);

		assertEquals(400, deselect(sale, second, ready.photos().get(0), Map.of()).status());
	}

	private long listReads(TestUser user) {
		return jdbc.queryForObject("select coalesce(sum(read_count), 0) from survey_media_read_budgets where user_id = ?"
				+ " and kind = 'LIST'", Long.class, user.userId());
	}

	/** A change answers outside the budget; a repeat changes nothing, so it is a read like any other. */
	@Test
	void deselection_repeated_countsInTheListBudget() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);
		String photo = ready.photos().get(0);
		long before = listReads(sale);

		SurveyApiTestSupport.ok(deselect(sale, second, photo, noCorrection()));
		assertEquals(before, listReads(sale));

		SurveyApiTestSupport.ok(deselect(sale, second, photo, noCorrection()));
		assertEquals(before + 1, listReads(sale));
	}

	/** Spec 5.5: a photo not carried into the path's revision is 404 before any other answer about the revision. */
	@Test
	void deselection_photoOfAnotherLead_404OnAReadOnlyRevision() throws Exception {
		Ready own = submitted();
		Ready foreign = review.ready(leader, sale);

		Result refused = deselect(sale, own.checklistId(), foreign.photos().get(0), noCorrection());

		assertEquals(404, refused.status(), refused.rawBody());
	}

	@Test
	void deselection_hiddenLead_404AndEvent() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);
		TestUser stranger = survey.user("Review Stranger", "SALE");

		assertEquals(404, deselect(stranger, second, ready.photos().get(0), noCorrection()).status());

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ? and action ="
				+ " 'DESELECT' and outcome = 'NOT_VISIBLE'", Integer.class, ready.leadId()));
	}

	@Test
	void deselection_unknownPhotoOfAHiddenLead_writesTheEvent() throws Exception {
		Ready ready = review.ready(leader, sale);
		TestUser stranger = survey.user("Review Stranger Two", "SALE");

		assertEquals(404, deselect(stranger, ready.checklistId(), checklists.newId(), noCorrection()).status());

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ? and action ="
				+ " 'DESELECT' and outcome = 'NOT_VISIBLE'", Integer.class, ready.leadId()));
	}

	/** Spec A-19: the throttle key names the revision, so fresh photo ids cannot write one row each. */
	@Test
	void deselection_freshPhotoIdsOnAHiddenLead_oneEvent() throws Exception {
		Ready ready = review.ready(leader, sale);
		TestUser stranger = survey.user("Review Stranger Three", "SALE");

		for (int i = 0; i < 3; i++) {
			assertEquals(404, deselect(stranger, ready.checklistId(), checklists.newId(), noCorrection()).status());
		}

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ? and action ="
				+ " 'DESELECT' and outcome = 'NOT_VISIBLE'", Integer.class, ready.leadId()));
	}

	@Test
	void deselection_notTheAssignee_403AndEvent() throws Exception {
		Ready ready = review.ready(leader, sale);
		String second = carriedInto(ready);

		assertEquals(403, deselect(manager, second, ready.photos().get(0), noCorrection()).status());

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ? and action ="
				+ " 'DESELECT' and outcome = 'NOT_ASSIGNEE'", Integer.class, ready.leadId()));
	}

	@Test
	void deselection_wrongRole_403() throws Exception {
		TestUser customer = survey.user("Review Customer", "CUSTOMER");

		assertEquals(403, deselect(customer, checklists.newId(), checklists.newId(), noCorrection()).status());
	}

	@Test
	void deselection_withoutToken_401() throws Exception {
		assertEquals(401, deselect(null, checklists.newId(), checklists.newId(), noCorrection()).status());
	}

	// ==================== error bodies (spec 7.3, ADR-010) ====================

	@Test
	void errors_submissionOff_field() throws Exception {
		Ready ready = review.ready(leader, sale);
		switches.submission(false);

		assertEnvelope(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_SUBMISSION_NOT_AVAILABLE", "submission");
	}

	@Test
	void errors_notReady_fieldPerGate() throws Exception {
		Ready ready = review.ready(leader, sale);
		media.created(sale, ready.checklistId(), MediaApiTestSupport.bytes(1500, 901));

		Result refused = review.submit(sale, ready.checklistId());

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_NOT_READY", refused.body().path("code").asString());
		assertFalse(refused.body().path("correlationId").asString().isBlank());
		List<String> fields = new ArrayList<>();
		refused.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		assertTrue(fields.contains("media.pending"), fields.toString());
	}

	@Test
	void errors_excluded_reviewerField() throws Exception {
		Ready ready = submitted();
		checklists.reassign(manager, ready.leadId(), leader);

		assertEnvelope(review.decide(leader, ready.checklistId(), "PASSED"), 403, "CHECKLIST_REVIEW_NOT_ALLOWED",
				"reviewer");
	}

	@Test
	void errors_decisionRefused_field() throws Exception {
		Ready ready = submitted();
		hold(ready.leadId());

		assertEnvelope(review.decide(manager, ready.checklistId(), "PASSED"), 409, "CHECKLIST_DECISION_NOT_ALLOWED",
				"lead.status");
	}
}
