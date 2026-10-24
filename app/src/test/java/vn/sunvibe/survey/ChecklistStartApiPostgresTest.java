package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Starting a checklist end to end (survey-checklist spec 7.1, 5.5; W16-1, W16-2, W16-3, W16-38 for the intake, A-11
 * and W16-20 for the start answer and its History row). Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistStartApiPostgresTest {

	private static final String GUESSED_ID = "01JZZZZZZZZZZZZZZZZZZZZZZZ";
	private static final List<String> START_FIELDS = List.of("checklist", "lead", "historyEntry");
	/** survey-checklist spec 7.2: the fields of LeadActivityResponse, in its order. */
	private static final List<String> HISTORY_ENTRY_FIELDS = List.of("type", "actorUserId", "actorName", "fromStatus",
			"toStatus", "fromAssigneeId", "fromAssigneeName", "toAssigneeId", "toAssigneeName", "note", "nextActionAt",
			"occurredAt", "referenceId", "referenceNo", "referenceDetail");
	private static final List<String> CHECKLIST_FIELDS = List.of("id", "leadId", "lead", "leadStaff", "checklistNo",
			"status", "kind", "templateCode", "templateVersion", "visit", "holderUserId", "holderName", "editable",
			"readOnlyReason", "groups", "readiness", "effectiveTerms", "earlierHazards", "ownerForm", "takeovers",
			"corrections", "editors", "createdByUserId", "createdByName", "createdAt", "version", "updatedAt");
	private static final List<String> GROUPS = List.of("identification", "parts", "dimensions", "condition", "safety",
			"amenities", "terms");

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

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private AcquisitionApiTestSupport api;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0961");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Start Sale", "SALE");
		otherSale = survey.user("Start Other Sale", "SALE");
		leader = survey.user("Start Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W16-1 ====================

	@Test
	void start_byThePerformingSale_createsAnEmptyDraft_andOneHistoryRow() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		long leadVersion = survey.version(visit.leadId());
		Instant lastActivityBefore = lastActivityAt(visit.leadId());

		Result started = checklists.start(sale, visit.appointmentId(), null);

		assertEquals(201, started.status(), started.rawBody());
		assertEquals(START_FIELDS, AcquisitionApiTestSupport.fieldNames(started.body()));
		JsonNode checklist = started.body().path("checklist");
		assertEquals(CHECKLIST_FIELDS, AcquisitionApiTestSupport.fieldNames(checklist));
		String id = checklist.path("id").asString();
		assertEquals(visit.leadId(), checklist.path("leadId").asString());
		assertEquals(1, checklist.path("checklistNo").asInt());
		assertEquals("DRAFT", checklist.path("status").asString());
		assertEquals("FULL_ASSESSMENT", checklist.path("kind").asString());
		assertEquals("LONG_TERM_RENT", checklist.path("templateCode").asString());
		assertEquals(1, checklist.path("templateVersion").asInt());
		assertEquals(sale.userId(), checklist.path("holderUserId").asString());
		assertEquals("Start Sale", checklist.path("holderName").asString());
		assertTrue(checklist.path("editable").asBoolean());
		assertTrue(checklist.path("readOnlyReason").isNull());
		assertEquals(visit.appointmentId(), checklist.path("visit").path("appointmentId").asString());
		assertEquals(sale.userId(), checklist.path("visit").path("performedByUserId").asString());
		assertEquals("OWN", checklist.path("visit").path("recordKind").asString());
		assertEquals(GROUPS, AcquisitionApiTestSupport.fieldNames(checklist.path("groups")));
		for (String group : GROUPS) {
			assertEquals(0, checklist.path("groups").path(group).path("version").asLong(), group);
			assertTrue(checklist.path("groups").path(group).path("updatedAt").isNull(), group);
		}
		JsonNode identification = checklist.path("groups").path("identification").path("content");
		assertEquals("UNKNOWN", identification.path("observedAddress").path("state").asString());
		assertTrue(identification.path("observedAddress").path("meta").isNull());
		assertEquals(0, identification.path("spaces").size());
		assertTrue(identification.path("rentalScope").isNull());
		assertEquals("UNKNOWN", checklist.path("groups").path("parts").path("content").path("accessRoute")
				.path("state").asString());
		assertEquals("UNKNOWN", checklist.path("groups").path("terms").path("content").path("common")
				.path("electricity").path("state").asString());
		assertFalse(checklist.path("readiness").path("submissionAvailable").asBoolean());
		assertEquals(0, checklist.path("takeovers").size());
		assertEquals(0, checklist.path("corrections").size());
		assertEquals(0, checklist.path("editors").size());
		assertEquals(0, checklist.path("version").asLong());

		JsonNode lead = started.body().path("lead");
		assertEquals("SURVEY_SCHEDULED", lead.path("status").asString());
		assertEquals(leadVersion, lead.path("version").asLong());
		assertEquals(leadVersion, survey.version(visit.leadId()));
		assertTrue(Instant.parse(lead.path("lastActivityAt").asString()).isAfter(lastActivityBefore));

		JsonNode entry = started.body().path("historyEntry");
		assertEquals(HISTORY_ENTRY_FIELDS, AcquisitionApiTestSupport.fieldNames(entry));
		assertEquals("CHECKLIST_STARTED", entry.path("type").asString());
		assertEquals(sale.userId(), entry.path("actorUserId").asString());
		assertEquals(id, entry.path("referenceId").asString());
		assertEquals(1, entry.path("referenceNo").asInt());
		assertTrue(entry.path("referenceDetail").isNull());
		assertTrue(entry.path("note").isNull());
		assertTrue(entry.path("fromStatus").isNull());

		List<Map<String, Object>> rows = checklists.activities(visit.leadId(), "CHECKLIST_STARTED");
		assertEquals(1, rows.size());
		assertEquals(id, rows.get(0).get("reference_id"));
		assertNull(rows.get(0).get("note"));
		assertNull(rows.get(0).get("reference_detail"));
		assertEquals(7, checklists.rows("select count(*) from survey_checklist_groups where checklist_id = ? and "
				+ "version = 0", id));
		assertEquals(0, checklists.rows("select count(*) from survey_checklist_takeovers where checklist_id = ?", id));
	}

	@Test
	void start_byAPerformingSaleLeaderAssignee_isAllowed() throws Exception {
		TestUser leaderAssignee = survey.user("Start Leader Assignee", "SALE_LEADER");
		Visit visit = checklists.visited(leader, leaderAssignee);

		Result started = checklists.start(leaderAssignee, visit.appointmentId(), null);

		assertEquals(201, started.status(), started.rawBody());
		assertEquals(leaderAssignee.userId(), started.body().path("checklist").path("holderUserId").asString());
	}

	// ==================== W16-2, W16-38, A-11 ====================

	@Test
	void start_byAnAssigneeWhoDidNotPerform_needsAnIntakeDeclaration() throws Exception {
		TestUser performer = survey.user("Start Performer", "SALE");
		TestUser newAssignee = survey.user("Start New Assignee", "SALE");
		Visit visit = checklists.visited(leader, performer);
		checklists.reassign(leader, visit.leadId(), newAssignee);
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);

		assertError(checklists.start(newAssignee, visit.appointmentId(), null), 400, "VALIDATION_FAILED", "intake");
		assertError(checklists.start(newAssignee, visit.appointmentId(), ChecklistApiTestSupport.intake(
				"Took over the lead", visit.arrivalDate().minusDays(1), "The performer told me")), 400,
				"VALIDATION_FAILED", "intake.basis.confirmedOn");
		assertError(checklists.start(newAssignee, visit.appointmentId(), ChecklistApiTestSupport.intake(
				"Took over the lead", today.plusDays(1), "The performer told me")), 400, "VALIDATION_FAILED",
				"intake.basis.confirmedOn");
		assertError(checklists.start(newAssignee, visit.appointmentId(), ChecklistApiTestSupport.intake(
				"call 0905 123 456", visit.arrivalDate(), "The performer told me")), 400, "VALIDATION_FAILED",
				"intake.reason");
		assertError(checklists.start(newAssignee, visit.appointmentId(), ChecklistApiTestSupport.intake(
				"Took over the lead", visit.arrivalDate(), "0905 123 456")), 400, "VALIDATION_FAILED",
				"intake.basis.text");
		assertEquals(0, checklists.rows("select count(*) from survey_checklists where lead_id = ?", visit.leadId()));

		Result started = checklists.start(newAssignee, visit.appointmentId(), ChecklistApiTestSupport.intake(
				"Took over the lead", visit.arrivalDate(), "The performer told me"));

		assertEquals(201, started.status(), started.rawBody());
		JsonNode takeover = started.body().path("checklist").path("takeovers").path(0);
		assertEquals(1, takeover.path("takeoverNo").asInt());
		assertEquals("START_BY_OTHER", takeover.path("kind").asString());
		assertEquals(newAssignee.userId(), takeover.path("toUserId").asString());
		assertFalse(takeover.path("hasSnapshot").asBoolean());
		assertEquals("Took over the lead", takeover.path("reason").asString());
		String id = started.body().path("checklist").path("id").asString();
		assertError(checklists.checkpoint(newAssignee, id, 1), 404, "NOT_FOUND", null);
		Map<String, Object> history = checklists.activities(visit.leadId(), "CHECKLIST_STARTED").get(0);
		assertNull(history.get("note"));
		assertFalse(started.body().path("historyEntry").toString().contains("performer told me"));
	}

	@Test
	void start_byThePerformerWithAnIntake_isRefusedOnIntake() throws Exception {
		Visit visit = checklists.visited(leader, sale);

		assertError(checklists.start(sale, visit.appointmentId(), ChecklistApiTestSupport.intake("Not needed",
				visit.arrivalDate(), "I was there")), 400, "VALIDATION_FAILED", "intake");
	}

	@Test
	void start_intakeShape_isCheckedBeforeTheAppointment() throws Exception {
		Result phoneOnAGuessedId = checklists.start(sale, GUESSED_ID, ChecklistApiTestSupport.intake(
				"call 0905 123 456", LocalDate.now(SurveyTimeRules.ZONE), "Told"));
		Map<String, Object> noIntakeKey = new LinkedHashMap<>();
		Result missingKey = api.post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + GUESSED_ID + "/checklists",
				noIntakeKey);

		assertError(phoneOnAGuessedId, 400, "VALIDATION_FAILED", "intake.reason");
		assertError(missingKey, 400, "VALIDATION_FAILED", "intake");
	}

	/** survey-checklist PD-2: checklist requests are strict, an unknown key is refused on its path. */
	@Test
	void start_andTakeover_refuseAnUnknownKey() throws Exception {
		Map<String, Object> start = new LinkedHashMap<>();
		start.put("intake", null);
		start.put("note", "free text");
		Map<String, Object> intake = ChecklistApiTestSupport.intake("Took over", LocalDate.now(SurveyTimeRules.ZONE),
				"Told");
		intake.put("phone", "0905123456");
		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("intake", intake);
		Map<String, Object> takeover = new LinkedHashMap<>();
		takeover.put("expectedVersion", 0);
		takeover.put("reason", "New assignee");
		takeover.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Told"));
		takeover.put("holder", sale.userId());

		Result unknownTop = api.post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + GUESSED_ID + "/checklists",
				start);
		Result unknownNested = api.post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + GUESSED_ID + "/checklists",
				nested);
		Result unknownTakeover = api.post(sale, ChecklistApiTestSupport.CHECKLISTS + "/" + GUESSED_ID + "/takeovers",
				takeover);

		assertError(unknownTop, 400, "VALIDATION_FAILED", "note");
		assertEquals("note is not a known field.", unknownTop.body().path("errors").path(0).path("message")
				.asString());
		assertError(unknownNested, 400, "VALIDATION_FAILED", "intake.phone");
		assertFalse(unknownNested.rawBody().contains("0905123456"));
		assertError(unknownTakeover, 400, "VALIDATION_FAILED", "holder");
	}

	// ==================== W16-3 ====================

	@Test
	void start_onAMissingOrHiddenAppointment_isNotFound() throws Exception {
		Visit ofOther = checklists.visited(leader, otherSale);

		assertError(checklists.start(sale, GUESSED_ID, null), 404, "NOT_FOUND", null);
		assertError(checklists.start(sale, "not-a-ulid", null), 404, "NOT_FOUND", null);
		assertError(checklists.start(sale, ofOther.appointmentId(), null), 404, "NOT_FOUND", null);
	}

	@Test
	void start_byALeaderWhoIsNotTheAssignee_isForbidden_beforeTheStateChecks() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String scheduledLead = survey.lead(leader, sale, "CONTACTED");
		String scheduled = survey.booked(sale, scheduledLead, SurveyApiTestSupport.startIn(24));

		assertEquals(403, checklists.start(leader, visit.appointmentId(), null).status());
		assertEquals(403, checklists.start(leader, scheduled, null).status());
	}

	@Test
	void start_onAnAppointmentThatIsNotTheCurrentVisit_isNotAllowedOnAppointmentId() throws Exception {
		String scheduledLead = survey.lead(leader, sale, "CONTACTED");
		String scheduled = survey.booked(sale, scheduledLead, SurveyApiTestSupport.startIn(24));
		String cancelledLead = survey.lead(leader, sale, "CONTACTED");
		String cancelled = survey.booked(sale, cancelledLead, SurveyApiTestSupport.startIn(24));
		Map<String, Object> cancel = new LinkedHashMap<>();
		cancel.put("expectedLeadVersion", survey.version(cancelledLead));
		cancel.put("expectedAppointmentVersion", survey.appointmentVersion(cancelled));
		cancel.put("cancelReason", "STAFF_CANCELLED");
		cancel.put("description", null);
		SurveyApiTestSupport.ok(api.post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + cancelled + "/cancellation",
				cancel));
		Visit first = checklists.visited(leader, sale);
		revisit(first.leadId());

		assertNotAllowed(checklists.start(sale, scheduled, null), "appointmentId", List.of());
		assertNotAllowed(checklists.start(sale, cancelled, null), "appointmentId", List.of());
		assertNotAllowed(checklists.start(sale, first.appointmentId(), null), "appointmentId", List.of());
	}

	@Test
	void start_onAShortTermVisit_isNotAllowedOnIntent() throws Exception {
		Visit visit = checklists.visited(leader, sale, "SHORT_TERM_RENT", null);

		assertNotAllowed(checklists.start(sale, visit.appointmentId(), null), "intent", List.of("LONG_TERM_RENT"));
	}

	@Test
	void start_whenTheLeadIsNotSurveyScheduled_isNotAllowedOnStatus() throws Exception {
		Visit held = checklists.visited(leader, sale);
		SurveyApiTestSupport.ok(survey.moveTo(leader, held.leadId(), "ON_HOLD"));
		Visit contacted = checklists.visited(leader, sale);
		SurveyApiTestSupport.ok(survey.moveTo(leader, contacted.leadId(), "ON_HOLD"));
		SurveyApiTestSupport.ok(survey.moveTo(leader, contacted.leadId(), "CONTACTED"));
		Visit closed = checklists.visited(leader, sale);
		Map<String, Object> reject = new LinkedHashMap<>();
		reject.put("toStatus", "REJECTED");
		reject.put("expectedVersion", survey.version(closed.leadId()));
		reject.put("reason", "Not a fit");
		SurveyApiTestSupport.ok(api.post(leader, SurveyApiTestSupport.LEADS + "/" + closed.leadId()
				+ "/status-changes", reject));

		for (Visit visit : List.of(held, contacted, closed)) {
			assertNotAllowed(checklists.start(sale, visit.appointmentId(), null), "status",
					List.of("SURVEY_SCHEDULED"));
		}
	}

	@Test
	void start_aSecondTime_isAlreadyStarted() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		checklists.started(sale, visit.appointmentId());

		assertError(checklists.start(sale, visit.appointmentId(), null), 409, "CHECKLIST_ALREADY_STARTED", null);
		assertEquals(1, checklists.rows("select count(*) from survey_checklists where lead_id = ?", visit.leadId()));
	}

	@Test
	void start_byAnAssigneeWhoIsNoLongerEligible_isSurveyorIneligible() throws Exception {
		TestUser leaving = survey.user("Start Leaving", "SALE");
		Visit visit = checklists.visited(leader, leaving);
		checklists.makeIneligible(leaving);

		assertError(checklists.start(leaving, visit.appointmentId(), null), 422, "SURVEYOR_INELIGIBLE", null);
	}

	// ==================== helpers ====================

	/** Holds the visited lead, moves it back to CONTACTED, books a re-survey and visits it: a newer visit. */
	private void revisit(String leadId) throws Exception {
		SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "ON_HOLD"));
		SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "CONTACTED"));
		Instant start = SurveyApiTestSupport.startIn(30);
		Map<String, Object> body = survey.booking(leadId, start);
		body.put("resurveyReason", "Owner rebuilt the kitchen");
		Result booked = survey.book(sale, leadId, body);
		assertEquals(201, booked.status(), booked.rawBody());
		String resurvey = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		survey.shiftBack(resurvey, Duration.ofDays(2));
		Result visited = checklists.recordVisit(sale, leadId, resurvey, start.minus(Duration.ofDays(2)));
		assertEquals(200, visited.status(), visited.rawBody());
	}

	private Instant lastActivityAt(String leadId) {
		return jdbc.queryForObject("select max(occurred_at) from owner_lead_activities where lead_id = ?",
				Timestamp.class, leadId)
				.toInstant();
	}

	private static void assertNotAllowed(Result result, String field, List<String> allowedValues) {
		assertError(result, 409, "CHECKLIST_NOT_ALLOWED", field);
		List<String> allowed = new ArrayList<>();
		result.body().path("errors").path(0).path("allowedValues").forEach(value -> allowed.add(value.asString()));
		assertEquals(allowedValues, allowed, result.rawBody());
	}
}
