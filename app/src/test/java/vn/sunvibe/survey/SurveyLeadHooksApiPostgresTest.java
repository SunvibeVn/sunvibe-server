package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.SurveyApiTestSupport.appointmentOf;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;
import static vn.sunvibe.survey.SurveyApiTestSupport.ok;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * The lead commands that move or cancel a scheduled survey appointment, end to end (survey-schedule spec 4.6, 5.4,
 * 5.5): reassignment (W15-12), hold and close (W15-13), the manual status changes of a SURVEY_SCHEDULED lead (W15-14,
 * W15-15) and the visits recorded around a reassignment (W15-16, W15-17, W15-18). Past times come from
 * {@link SurveyApiTestSupport#shiftBack}. Needs Docker.
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
class SurveyLeadHooksApiPostgresTest {

	private static final String LEADS = SurveyApiTestSupport.LEADS;
	private static final String APPOINTMENTS = SurveyApiTestSupport.APPOINTMENTS;
	private static final List<String> HOLD_OR_CLOSE = List.of("ON_HOLD", "DUPLICATE", "REJECTED", "WITHDRAWN");

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
	private AcquisitionApiTestSupport api;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0975");
		saleA = survey.user("Hooks Sale A", "SALE");
		saleB = survey.user("Hooks Sale B", "SALE");
		leader = survey.user("Hooks Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W15-12: reassignment ====================

	@Test
	void reassign_scheduledAppointment_followsTheNewAssignee_andLeavesThePreviousOne() throws Exception {
		Booked booked = booked(saleA, true);

		ok(reassign(booked.leadId(), saleB));

		JsonNode appointment = appointmentOf(block(saleB, booked.leadId()), booked.id());
		assertEquals(saleB.userId(), appointment.path("surveyorUserId").asString());
		assertEquals("UNCONFIRMED", appointment.path("confirmation").path("state").asString());
		assertEquals("REASSIGNED", appointment.path("confirmation").path("cause").asString());
		JsonNode surveyorChanged = lastEvent(appointment);
		assertEquals("SURVEYOR_CHANGED", surveyorChanged.path("type").asString());
		assertEquals(saleA.userId(), surveyorChanged.path("fromSurveyorUserId").asString());
		assertEquals(saleB.userId(), surveyorChanged.path("toSurveyorUserId").asString());
		assertHistoryNames(booked, "REASSIGNED");

		assertEquals(404, api.get(saleA, LEADS + "/" + booked.leadId() + "/survey").status());
		assertEquals(404, api.post(saleA, APPOINTMENTS + "/" + booked.id() + "/confirmation", Map.of(
				"expectedLeadVersion", survey.version(booked.leadId()), "expectedAppointmentVersion",
				survey.appointmentVersion(booked.id()), "confirmed", true)).status());
		for (Result refused : previousAssigneesCommands(booked)) {
			assertError(refused, 404, "NOT_FOUND", null);
		}
		assertFalse(listedIds(saleA).contains(booked.id()));
		assertTrue(listedIds(saleB).contains(booked.id()));
	}

	@Test
	void reassign_overdueAppointment_followsTheNewAssignee() throws Exception {
		Booked booked = booked(saleA, false);
		survey.shiftBack(booked.id(), Duration.ofDays(3));

		ok(reassign(booked.leadId(), saleB));

		JsonNode appointment = appointmentOf(block(leader, booked.leadId()), booked.id());
		assertEquals(saleB.userId(), appointment.path("surveyorUserId").asString());
		assertEquals("SCHEDULED", appointment.path("status").asString());
		assertHistoryNames(booked, "REASSIGNED");
	}

	// ==================== W15-13: hold and close ====================

	/** Review Focus 2: a hold note of 2000 characters holding a phone number is kept on the cancelled appointment. */
	@Test
	void hold_withLongNote_cancelsAppointmentKeepingNote() throws Exception {
		Booked booked = booked(saleA, false);
		String note = "Chủ nhà đi công tác, gọi lại 0905 123 456 sau. ";
		note = note + "x".repeat(2000 - note.length());

		Result held = api.post(saleA, LEADS + "/" + booked.leadId() + "/status-changes", Map.of("toStatus", "ON_HOLD",
				"expectedVersion", survey.version(booked.leadId()), "note", note));

		ok(held);
		assertEquals("ON_HOLD", held.body().path("status").asString());
		JsonNode appointment = appointmentOf(block(saleA, booked.leadId()), booked.id());
		assertEquals("CANCELLED", appointment.path("status").asString());
		assertEquals("LEAD_ON_HOLD", appointment.path("cancellation").path("cancelReason").asString());
		assertEquals(note, appointment.path("cancellation").path("description").asString());
		assertEquals(saleA.userId(), appointment.path("cancellation").path("cancelledByUserId").asString());
		JsonNode entry = assertHistoryNames(booked, "STATUS_CHANGED");
		assertEquals("SURVEY_SCHEDULED", entry.path("fromStatus").asString());
		assertEquals("ON_HOLD", entry.path("toStatus").asString());
	}

	@Test
	void hold_withoutNote_cancelsWithoutDescription() throws Exception {
		Booked booked = booked(saleA, false);

		ok(survey.moveTo(saleA, booked.leadId(), "ON_HOLD"));

		JsonNode cancellation = appointmentOf(block(saleA, booked.leadId()), booked.id()).path("cancellation");
		assertEquals("LEAD_ON_HOLD", cancellation.path("cancelReason").asString());
		assertTrue(cancellation.path("description").isNull());
	}

	@Test
	void close_cancelsWithLeadClosedKeepingTheReason() throws Exception {
		Booked booked = booked(saleA, false);

		ok(api.post(leader, LEADS + "/" + booked.leadId() + "/status-changes", Map.of("toStatus", "REJECTED",
				"expectedVersion", survey.version(booked.leadId()), "reason", "Owner sold the house")));

		JsonNode appointment = appointmentOf(block(leader, booked.leadId()), booked.id());
		assertEquals("CANCELLED", appointment.path("status").asString());
		assertEquals("LEAD_CLOSED", appointment.path("cancellation").path("cancelReason").asString());
		assertEquals("Owner sold the house", appointment.path("cancellation").path("description").asString());
		assertHistoryNames(booked, "STATUS_CHANGED");
	}

	// ==================== W15-14, W15-15 ====================

	@Test
	void statusChanges_fromSurveyScheduled_allowOnlyHoldAndClosing() throws Exception {
		Booked booked = booked(saleA, false);
		String contacted = survey.lead(leader, saleA, "CONTACTED");
		String assigned = survey.lead(leader, saleA, "ASSIGNED");

		for (String to : List.of("CONTACTED", "SURVEY_SCHEDULED", "ASSIGNED")) {
			Result refused = survey.moveTo(leader, booked.leadId(), to);
			assertError(refused, 409, "INVALID_STATE_TRANSITION", "toStatus");
			assertEquals(HOLD_OR_CLOSE, allowed(refused));
		}
		Result fromContacted = survey.moveTo(leader, contacted, "SURVEY_SCHEDULED");
		assertError(fromContacted, 409, "INVALID_STATE_TRANSITION", "toStatus");
		assertEquals(HOLD_OR_CLOSE, allowed(fromContacted));
		Result fromAssigned = survey.moveTo(leader, assigned, "SURVEY_SCHEDULED");
		assertError(fromAssigned, 409, "INVALID_STATE_TRANSITION", "toStatus");
		assertEquals(List.of("CONTACTED", "ON_HOLD", "DUPLICATE", "REJECTED", "WITHDRAWN"), allowed(fromAssigned));
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(booked.leadId()));
	}

	@Test
	void surveyScheduledLead_isOpen_forNotesAndDuplicateDetection() throws Exception {
		Booked booked = booked(saleA, false);

		Result note = api.post(saleA, LEADS + "/" + booked.leadId() + "/activities", Map.of("note", "Owner called"));
		String repeat = survey.submitted(survey.phone(booked.leadId()));

		assertEquals(201, note.status(), note.rawBody());
		JsonNode detail = api.get(leader, LEADS + "/" + repeat).body();
		assertTrue(detail.path("possibleDuplicate").asBoolean(), detail.toString());
		assertEquals(booked.leadId(), detail.path("duplicateCandidates").path(0).path("id").asString());
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(booked.leadId()));
	}

	// ==================== W15-16, W15-17, W15-18: visits around a reassignment ====================

	/** Review Focus 1: an arrival in the minute of the reassignment finds the new holder. */
	@Test
	void ownVisit_inTheReassignmentMinute_isTheNewHoldersVisit() throws Exception {
		Booked booked = reassignedAfterTwoDays(saleA, saleB);
		Instant reassignedMinute = reassignmentMinute(booked.id());

		Result visited = api.post(saleB, visitPath(booked.id()), visit(booked, reassignedMinute, saleB, null));

		ok(visited);
		JsonNode visit = appointmentOf(visited.body().path("survey"), booked.id()).path("visit");
		assertEquals("OWN", visit.path("recordKind").asString());
		assertEquals(saleB.userId(), visit.path("performedByUserId").asString());
		assertEquals(reassignedMinute, Instant.parse(visit.path("arrivedAt").asString()));
	}

	@Test
	void proxyVisit_forAPerformerReassignedAway_isAcceptedWhenTheyHeldItThen() throws Exception {
		Booked booked = reassignedAfterTwoDays(saleA, saleB);

		Result visited = api.post(leader, visitPath(booked.id()), visit(booked, booked.start(), saleA,
				proxy(booked.start())));

		ok(visited);
		JsonNode visit = appointmentOf(visited.body().path("survey"), booked.id()).path("visit");
		assertEquals("PROXY", visit.path("recordKind").asString());
		assertEquals(saleA.userId(), visit.path("performedByUserId").asString());
	}

	@Test
	void ownVisitOfALeader_afterAReassignment_needsReasonAndBasis() throws Exception {
		TestUser surveyingLeader = survey.user("Hooks Surveying Leader", "SALE_LEADER");
		Booked booked = reassignedAfterTwoDays(surveyingLeader, saleB);

		Result withoutProxy = api.post(surveyingLeader, visitPath(booked.id()), visit(booked, booked.start(),
				surveyingLeader, null));
		Result withProxy = api.post(surveyingLeader, visitPath(booked.id()), visit(booked, booked.start(),
				surveyingLeader, proxy(booked.start())));

		assertError(withoutProxy, 400, "VALIDATION_FAILED", "proxy");
		ok(withProxy);
		assertEquals("PROXY", appointmentOf(withProxy.body().path("survey"), booked.id()).path("visit")
				.path("recordKind").asString());
	}

	@Test
	void newAssignee_cannotRecordAVisitHeldByThePreviousOne() throws Exception {
		Booked booked = reassignedAfterTwoDays(saleA, saleB);

		Result refused = api.post(saleB, visitPath(booked.id()), visit(booked, booked.start(), saleB, null));

		assertError(refused, 422, "PERFORMER_MISMATCH", "performedByUserId");
		assertEquals("SCHEDULED", appointmentOf(block(saleB, booked.leadId()), booked.id()).path("status")
				.asString());
	}

	/**
	 * The security chain reads the caller's roles on every request, so a caller who lost every staff role is refused
	 * before the survey rules run; one who keeps a role without lead work (SALES_MANAGER) reaches the eligibility
	 * check.
	 */
	@Test
	void ownVisit_ofACallerNoLongerEligible_isRefused() throws Exception {
		TestUser leaving = survey.user("Hooks Leaving Sale", "SALE", "SALES_MANAGER");
		Booked booked = reassignedAfterTwoDays(saleA, leaving);
		Instant reassignedMinute = reassignmentMinute(booked.id());
		assertEquals(1, jdbc.update("delete from user_roles where user_id = ? "
				+ "and role_id = (select id from roles where name = 'SALE')", leaving.userId()));

		Result refused = api.post(leaving, visitPath(booked.id()), visit(booked, reassignedMinute, leaving, null));

		assertError(refused, 422, "SURVEYOR_INELIGIBLE", "performedByUserId");
	}

	// ==================== helpers ====================

	/** A booked appointment as the tests know it: lead, id and start (shifted when the test moved it back). */
	private record Booked(String leadId, String id, Instant start) {
	}

	/** A CONTACTED lead of the surveyor with an appointment booked by them at the next day's same hour. */
	private Booked booked(TestUser surveyor, boolean ownerConfirmed) throws Exception {
		String leadId = survey.lead(leader, surveyor, "CONTACTED");
		Instant start = SurveyApiTestSupport.startIn(24);
		Map<String, Object> body = survey.booking(leadId, start);
		body.put("ownerConfirmed", ownerConfirmed);
		Result result = survey.book(surveyor, leadId, body);
		assertEquals(201, result.status(), result.rawBody());
		return new Booked(leadId, result.body().path("survey").path("appointments").path(0).path("id").asString(),
				start);
	}

	/** Booked by the first holder, moved two days back, then the lead given to the second. */
	private Booked reassignedAfterTwoDays(TestUser first, TestUser second) throws Exception {
		Booked booked = booked(first, false);
		survey.shiftBack(booked.id(), Duration.ofDays(2));
		ok(reassign(booked.leadId(), second));
		return new Booked(booked.leadId(), booked.id(), booked.start().minus(Duration.ofDays(2)));
	}

	/**
	 * Every other survey and lead command of Sale A, with a valid body, on the lead they no longer hold: change,
	 * cancel, own visit, the overlap pre-check, resume and requirement change.
	 */
	private List<Result> previousAssigneesCommands(Booked booked) throws Exception {
		String leadId = booked.leadId();
		long leadVersion = survey.version(leadId);
		long appointmentVersion = survey.appointmentVersion(booked.id());
		Map<String, Object> row = jdbc.queryForMap("select address_detail, province_code, ward_code, latitude, "
				+ "longitude from owner_leads where id = ?", leadId);
		Map<String, Object> place = new LinkedHashMap<>();
		place.put("addressText", row.get("address_detail"));
		place.put("provinceCode", row.get("province_code"));
		place.put("wardCode", row.get("ward_code"));
		place.put("latitude", row.get("latitude"));
		place.put("longitude", row.get("longitude"));
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("expectedLeadVersion", leadVersion);
		change.put("expectedAppointmentVersion", appointmentVersion);
		change.put("reason", "Later");
		change.put("startAt", booked.start().plus(Duration.ofHours(1)).toString());
		change.put("durationMinutes", 60);
		change.put("meetingLocation", place);
		change.put("accessNote", null);
		change.put("onSiteContactName", null);
		change.put("onSiteContactRole", null);
		Map<String, Object> cancel = new LinkedHashMap<>();
		cancel.put("expectedLeadVersion", leadVersion);
		cancel.put("expectedAppointmentVersion", appointmentVersion);
		cancel.put("cancelReason", "STAFF_CANCELLED");
		Map<String, Object> basis = Map.of("confirmedOn", LocalDate.now(SurveyTimeRules.ZONE).toString(), "text",
				"Owner said so");
		Map<String, Object> intent = new LinkedHashMap<>();
		intent.put("expectedVersion", leadVersion);
		intent.put("newIntent", "SHORT_TERM_RENT");
		intent.put("askingPrice", null);
		intent.put("reason", "Short stays");
		intent.put("basis", basis);
		return List.of(
				api.post(saleA, APPOINTMENTS + "/" + booked.id() + "/changes", change),
				api.post(saleA, APPOINTMENTS + "/" + booked.id() + "/cancellation", cancel),
				api.post(saleA, visitPath(booked.id()), visit(booked, booked.start(), saleA, null)),
				api.get(saleA, LEADS + "/" + leadId + "/survey-overlaps?startAt=" + booked.start()
						+ "&durationMinutes=60"),
				api.post(saleA, LEADS + "/" + leadId + "/survey-resumptions", Map.of("expectedLeadVersion",
						leadVersion, "reason", "Back", "basis", basis)),
				api.post(saleA, LEADS + "/" + leadId + "/intent-changes", intent));
	}

	private Result reassign(String leadId, TestUser to) throws Exception {
		return api.post(leader, LEADS + "/" + leadId + "/assign", Map.of("assigneeUserId", to.userId(),
				"expectedVersion", survey.version(leadId)));
	}

	private Instant reassignmentMinute(String appointmentId) {
		return jdbc.queryForObject("select occurred_at from survey_appointment_events where appointment_id = ? and "
				+ "type = 'SURVEYOR_CHANGED'", java.sql.Timestamp.class, appointmentId).toInstant()
				.truncatedTo(ChronoUnit.MINUTES);
	}

	private JsonNode block(TestUser caller, String leadId) throws Exception {
		Result result = api.get(caller, LEADS + "/" + leadId + "/survey");
		ok(result);
		return result.body();
	}

	private static JsonNode lastEvent(JsonNode appointment) {
		JsonNode events = appointment.path("events");
		return events.path(events.size() - 1);
	}

	/**
	 * The lead's History has exactly one row of the type naming the appointment, and it is the latest row; returns
	 * it.
	 */
	private JsonNode assertHistoryNames(Booked booked, String type) throws Exception {
		assertEquals(1, jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ? and type = ? "
				+ "and reference_id = ?", Integer.class, booked.leadId(), type, booked.id()));
		JsonNode activities = api.get(leader, LEADS + "/" + booked.leadId()).body().path("activities");
		JsonNode latest = activities.path(activities.size() - 1);
		assertEquals(type, latest.path("type").asString(), activities.toString());
		assertEquals(booked.id(), latest.path("referenceId").asString());
		assertEquals(1, latest.path("referenceNo").asInt());
		return latest;
	}

	private List<String> listedIds(TestUser caller) throws Exception {
		Result result = api.get(caller, APPOINTMENTS + "?scope=mine&size=100");
		ok(result);
		List<String> ids = new ArrayList<>();
		result.body().path("items").forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	private static List<String> allowed(Result result) {
		List<String> values = new ArrayList<>();
		result.body().path("errors").path(0).path("allowedValues").forEach(value -> values.add(value.asString()));
		return values;
	}

	private static String visitPath(String appointmentId) {
		return APPOINTMENTS + "/" + appointmentId + "/visit";
	}

	private Map<String, Object> visit(Booked booked, Instant arrivedAt, TestUser performer,
			Map<String, Object> proxyOrNull) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(booked.leadId()));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(booked.id()));
		body.put("arrivedAt", arrivedAt.toString());
		body.put("note", "Met the owner");
		body.put("performedByUserId", performer.userId());
		if (proxyOrNull != null) {
			body.put("proxy", proxyOrNull);
		}
		return body;
	}

	private static Map<String, Object> proxy(Instant arrivedAt) {
		return Map.of("reason", "Entered after the lead moved", "basis", Map.of("confirmedOn",
				SurveyTimeRules.vietnamDate(arrivedAt).toString(), "text", "Sale reported the visit"));
	}
}
