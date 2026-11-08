package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
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

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;

/**
 * What the survey record does to the lead's other commands (survey-checklist submission spec 4.8, S-9, S-11, S-14..S-16):
 * booking, cancel and resume follow the record, QUALIFIED is open with its exits, and a held lead keeps to its record.
 * Needs Docker.
 */
class ChecklistReviewLeadRulesPostgresTest extends MediaApiTestBase {

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

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0965");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Rules Sale", "SALE");
		otherSale = survey.user("Rules Other Sale", "SALE");
		leader = survey.user("Rules Leader", "SALE_LEADER");
		manager = survey.user("Rules Manager", "SALES_MANAGER");
		admin = survey.user("Rules Admin", "ADMIN");
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

	private Ready passed() throws Exception {
		Ready ready = submitted();
		review.passed(leader, ready.checklistId());
		return ready;
	}

	private Result rebook(String leadId) throws Exception {
		Map<String, Object> booking = new LinkedHashMap<>(survey.booking(leadId, SurveyApiTestSupport.startIn(24)));
		booking.put("resurveyReason", "The reviewer asked for a new visit");
		return survey.book(sale, leadId, booking);
	}

	private void hold(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "ON_HOLD");
		body.put("expectedVersion", survey.version(leadId));
		body.put("note", "Owner abroad");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId
				+ "/status-changes", body));
	}

	private Result resume(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(leadId));
		body.put("reason", "Owner is back");
		body.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		return checklists.api().post(sale, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-resumptions", body);
	}

	private Result toStatus(String leadId, String status) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", status);
		body.put("expectedVersion", survey.version(leadId));
		if (List.of("REJECTED", "WITHDRAWN").contains(status)) {
			body.put("reason", "Owner changed their mind");
		}
		return checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/status-changes", body);
	}

	private Result changeIntent(String leadId) throws Exception {
		return changeIntent(leadId, "SHORT_TERM_RENT");
	}

	private Result changeIntent(String leadId, String intent) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", survey.version(leadId));
		body.put("newIntent", intent);
		body.put("askingPrice", null);
		body.put("reason", "Owner changed plans");
		body.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		return checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes", body);
	}

	private static void assertChecklistRefusal(Result refused) {
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("SURVEY_NOT_ALLOWED", refused.body().path("code").asString(), refused.rawBody());
		assertEquals("checklist", refused.body().path("errors").path(0).path("field").asString(), refused.rawBody());
		assertTrue(refused.body().path("errors").path(0).path("allowedValues").isEmpty(), refused.rawBody());
	}

	// ==================== booking (spec 4.8, S-14) ====================

	@Test
	void book_withSubmitted_refused() throws Exception {
		Ready ready = submitted();

		assertChecklistRefusal(rebook(ready.leadId()));
	}

	@Test
	void book_withPassed_refused() throws Exception {
		Ready ready = passed();

		assertChecklistRefusal(rebook(ready.leadId()));
	}

	@Test
	void book_withHeldPassed_refused() throws Exception {
		Ready ready = passed();
		media.held(ready.photos().get(0), admin.userId(), "SECURITY_INCIDENT");
		hold(ready.leadId());

		assertChecklistRefusal(rebook(ready.leadId()));
	}

	@Test
	void book_afterDocumentsOnly_allowed() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");

		assertEquals(201, rebook(ready.leadId()).status());
	}

	// ==================== cancel (spec 4.8, S-11) ====================

	private String cancelBody(String leadId, String appointmentId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(leadId));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(appointmentId));
		body.put("cancelReason", "OWNER_CANCELLED");
		body.put("description", "Owner cancelled");
		SurveyApiTestSupport.ok(checklists.api().post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + appointmentId
				+ "/cancellation", body));
		return survey.leadStatus(leadId);
	}

	@Test
	void cancel_afterResurveyMark_movesToContacted() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "RESURVEY_REQUIRED");
		Result booked = rebook(ready.leadId());
		assertEquals(201, booked.status(), booked.rawBody());
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(ready.leadId()));

		String status = cancelBody(ready.leadId(), booked.body().path("survey").path("appointments").path(0).path("id")
				.asString());

		assertEquals("CONTACTED", status, "no path leads to PASSED on the old visit");
	}

	@Test
	void cancel_withDraftRevision_keepsScheduled() throws Exception {
		Ready ready = review.ready(leader, sale);
		Result booked = rebook(ready.leadId());
		assertEquals(201, booked.status(), booked.rawBody());

		assertEquals("SURVEY_SCHEDULED", cancelBody(ready.leadId(), booked.body().path("survey").path("appointments")
				.path(0).path("id").asString()));
	}

	// ==================== resume (spec 4.8, plan PD-7, S-16) ====================

	@Test
	void resume_usablePassed_toQualified() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());

		Result resumed = resume(ready.leadId());

		assertEquals(201, resumed.status(), resumed.rawBody());
		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
	}

	@Test
	void resume_heldPassed_refused_untilTheRelease() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());
		media.held(ready.photos().get(0), admin.userId(), "SECURITY_INCIDENT");

		assertChecklistRefusal(resume(ready.leadId()));
		assertEquals("ON_HOLD", survey.leadStatus(ready.leadId()));

		jdbc.update("update survey_media set hold_released_at = held_at + interval '1 second',"
				+ " hold_released_by_user_id = ? where id = ?", admin.userId(), ready.photos().get(0));

		assertEquals(201, resume(ready.leadId()).status());
		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
	}

	@Test
	void resume_completable_toSurveyScheduled() throws Exception {
		Ready ready = submitted();
		hold(ready.leadId());

		assertEquals(201, resume(ready.leadId()).status());

		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(ready.leadId()));
	}

	/** plan PD-7: a visit returned for a re-survey cannot be picked up again. */
	@Test
	void resume_visitWithResurveyMark_refusedChecklist() throws Exception {
		Ready ready = submitted();
		review.needsChanges(leader, ready.checklistId(), "RESURVEY_REQUIRED");
		hold(ready.leadId());

		assertChecklistRefusal(resume(ready.leadId()));
	}

	/** S-9: a held lead takes no decision; resumed, it does. */
	@Test
	void decision_afterResume_allowedAgain() throws Exception {
		Ready ready = submitted();
		hold(ready.leadId());
		assertEquals(201, resume(ready.leadId()).status());

		review.passed(manager, ready.checklistId());

		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
	}

	// ==================== QUALIFIED (module BR-12, S-15, S-16) ====================

	private static List<String> allowed(Result refused) {
		List<String> values = new ArrayList<>();
		refused.body().path("errors").path(0).path("allowedValues").forEach(value -> values.add(value.asString()));
		return values;
	}

	@Test
	void qualified_takesHoldClosingNotesAndReassign() throws Exception {
		Ready ready = passed();

		assertEquals(201, checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/activities", Map.of("note", "Owner sent the keys")).status());
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/assign", Map.of("assigneeUserId", otherSale.userId(), "expectedVersion",
				survey.version(ready.leadId()))));
		assertEquals(200, toStatus(ready.leadId(), "WITHDRAWN").status());
	}

	@Test
	void qualified_noManualMoveTo() throws Exception {
		Ready ready = review.ready(leader, sale);

		Result refused = toStatus(ready.leadId(), "QUALIFIED");

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("INVALID_STATE_TRANSITION", refused.body().path("code").asString());
	}

	@Test
	void onHoldToContacted_withPassed_refused_allowedWithoutContacted() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());

		Result refused = toStatus(ready.leadId(), "CONTACTED");

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("toStatus", refused.body().path("errors").path(0).path("field").asString());
		assertEquals(List.of("DUPLICATE", "REJECTED", "WITHDRAWN"), allowed(refused));
		assertEquals("ON_HOLD", survey.leadStatus(ready.leadId()));
	}

	@Test
	void onHoldToContacted_withSubmitted_refused() throws Exception {
		Ready ready = submitted();
		hold(ready.leadId());

		assertEquals(409, toStatus(ready.leadId(), "CONTACTED").status());
	}

	@Test
	void onHoldToContacted_noRecord_allowed() throws Exception {
		Ready ready = review.ready(leader, sale);
		hold(ready.leadId());

		assertEquals(200, toStatus(ready.leadId(), "CONTACTED").status());
	}

	@Test
	void intentChange_withPassed_refused_emptyAllowed() throws Exception {
		Ready ready = passed();

		Result refused = changeIntent(ready.leadId());

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("INVALID_STATE_TRANSITION", refused.body().path("code").asString());
		assertEquals("status", refused.body().path("errors").path(0).path("field").asString());
		assertEquals(List.of(), allowed(refused));
	}

	@Test
	void intentChange_heldLeadWithPassed_refused_emptyAllowed() throws Exception {
		Ready ready = passed();
		hold(ready.leadId());

		Result refused = changeIntent(ready.leadId());

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals(List.of(), allowed(refused));
		assertEquals(1, jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class,
				ready.leadId()), "the refusal rolled the change back");
	}

	/** S-9: the change ends the revision's cycle; a later submit of the lead is not blocked by it. */
	@Test
	void intentChange_withSubmitted_endsTheCycle() throws Exception {
		Ready ready = submitted();

		SurveyApiTestSupport.ok(changeIntent(ready.leadId()));

		assertEquals(2, jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class,
				ready.leadId()));
		Result refused = review.decide(leader, ready.checklistId(), "PASSED");
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_DECISION_NOT_ALLOWED", refused.body().path("code").asString());
	}

	/** S-9, spec 4.2: the one-SUBMITTED rule is per cycle, so the ended cycle's revision blocks nothing in the new one. */
	@Test
	void submit_newCycle_notBlockedByTheEndedCyclesSubmitted() throws Exception {
		Ready ready = submitted();
		SurveyApiTestSupport.ok(changeIntent(ready.leadId(), "SHORT_TERM_RENT"));
		SurveyApiTestSupport.ok(changeIntent(ready.leadId(), "LONG_TERM_RENT"));

		Instant start = SurveyApiTestSupport.startIn(24);
		Result booked = survey.book(sale, ready.leadId(), survey.booking(ready.leadId(), start));
		assertEquals(201, booked.status(), booked.rawBody());
		String appointment = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		Duration back = Duration.ofDays(2).plusHours(1);
		survey.shiftBack(appointment, back);
		Result visited = checklists.recordVisit(sale, ready.leadId(), appointment, start.minus(back));
		assertEquals(200, visited.status(), visited.rawBody());
		String id = checklists.started(sale, appointment);
		String space = checklists.newId();
		review.fill(sale, id, space);
		review.photos(sale, id, space);

		review.submitted(sale, id);

		assertEquals(2, jdbc.queryForObject("select count(*) from survey_checklists where lead_id = ? and status ="
				+ " 'SUBMITTED'", Integer.class, ready.leadId()));
	}
}
