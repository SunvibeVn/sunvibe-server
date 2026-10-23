package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Change, confirm, cancel, visit and resume end to end (survey-schedule spec 7.1; W15-7 for the command fields, W15-8
 * for finished appointments, W15-9, W15-10, W15-11, W15-16, W15-17, W15-19, W15-20, W15-21, W15-24 and W15-26 for the
 * command paths, W15-27 for change and confirm). Past times come from {@link SurveyApiTestSupport#shiftBack}. Needs
 * Docker.
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
class SurveyAppointmentCommandsApiPostgresTest {

	private static final String APPOINTMENTS = SurveyApiTestSupport.APPOINTMENTS;
	private static final String GUESSED_ID = "01JZZZZZZZZZZZZZZZZZZZZZZZ";

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
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0974");
		sale = survey.user("Command Sale", "SALE");
		otherSale = survey.user("Command Other Sale", "SALE");
		leader = survey.user("Command Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W15-9: change ====================

	@Test
	void change_time_addsAFullRevisionAndRemovesTheConfirmation() throws Exception {
		Booked booked = bookedConfirmed();
		Instant newStart = booked.start().plus(Duration.ofHours(2));

		Result result = api.post(sale, path(booked.id(), "changes"), change(booked, "Owner asked for later", newStart,
				90, null, "Ring twice", "Anh Tuan", "Guard"));

		assertEquals(200, result.status(), result.rawBody());
		JsonNode appointment = appointmentOf(result, booked.id());
		assertEquals(2, appointment.path("revisions").size());
		JsonNode revision = appointment.path("revisions").path(1);
		assertEquals(newStart, Instant.parse(revision.path("startAt").asString()));
		assertEquals(90, revision.path("durationMinutes").asInt());
		assertEquals("Ring twice", revision.path("accessNote").asString());
		assertEquals("Owner asked for later", revision.path("reason").asString());
		assertEquals(booked.start(), Instant.parse(appointment.path("revisions").path(0).path("startAt").asString()));
		assertEquals("UNCONFIRMED", appointment.path("confirmation").path("state").asString());
		assertEquals("TIME_CHANGED", appointment.path("confirmation").path("cause").asString());
		assertTrue(appointment.path("confirmation").path("everConfirmed").asBoolean());
		assertEquals("SURVEY_CHANGED", result.body().path("historyEntry").path("type").asString());
		assertTrue(result.body().path("historyEntry").path("toStatus").isNull());
		assertEquals(booked.leadVersion() + 1, result.body().path("lead").path("version").asLong());
	}

	@Test
	void change_placeOnly_isLocationChanged_noteOnly_keepsTheConfirmation() throws Exception {
		Booked place = bookedConfirmed();
		Map<String, Object> otherPlace = place("Cafe 3B", SurveyApiTestSupport.OTHER_WARD);
		Result moved = api.post(sale, path(place.id(), "changes"), change(place, "Meet at the cafe", place.start(), 60,
				otherPlace, null, null, null));
		assertEquals("LOCATION_CHANGED", appointmentOf(moved, place.id()).path("confirmation").path("cause")
				.asString(), moved.rawBody());

		Booked note = bookedConfirmed();
		Result noted = api.post(sale, path(note.id(), "changes"), change(note, "Gate hint", note.start(), 60,
				null, "Side gate", null, null));
		assertEquals("CONFIRMED", appointmentOf(noted, note.id()).path("confirmation").path("state").asString(),
				noted.rawBody());
		assertEquals(0, noted.body().path("overlaps").size());
	}

	@Test
	void change_refusals() throws Exception {
		Booked booked = bookedConfirmed();
		Instant later = booked.start().plus(Duration.ofHours(1));

		Map<String, Object> noReason = change(booked, "  ", later, 60, null, null, null, null);
		assertError(api.post(sale, path(booked.id(), "changes"), noReason), 400, "VALIDATION_FAILED", "reason");

		Result nothing = api.post(sale, path(booked.id(), "changes"), change(booked, "Same", booked.start(), 60,
				null, null, null, null));
		assertEquals(400, nothing.status(), nothing.rawBody());
		assertEquals("Nothing changed.", nothing.body().path("message").asString());
		assertEquals(0, nothing.body().path("errors").size());

		Map<String, Object> missingKey = change(booked, "Later", later, 60, null, null, null, null);
		missingKey.remove("accessNote");
		Result missing = api.post(sale, path(booked.id(), "changes"), missingKey);
		assertError(missing, 400, "VALIDATION_FAILED", "accessNote");
		assertEquals("accessNote is required.", missing.body().path("errors").path(0).path("message").asString());

		Map<String, Object> staleLead = change(booked, "Later", later, 60, null, null, null, null);
		staleLead.put("expectedLeadVersion", booked.leadVersion() - 1);
		assertError(api.post(sale, path(booked.id(), "changes"), staleLead), 409, "CONCURRENT_MODIFICATION",
				"expectedLeadVersion");
		Map<String, Object> staleAppointment = change(booked, "Later", later, 60, null, null, null, null);
		staleAppointment.put("expectedAppointmentVersion", 9);
		assertError(api.post(sale, path(booked.id(), "changes"), staleAppointment), 409, "CONCURRENT_MODIFICATION",
				"expectedAppointmentVersion");

		for (String field : List.of("accessNote", "onSiteContactName", "onSiteContactRole")) {
			Map<String, Object> body = change(booked, "Later", later, 60, null, null, null, null);
			body.put(field, "0905 123 456");
			assertError(api.post(sale, path(booked.id(), "changes"), body), 400, "VALIDATION_FAILED", field);
		}
		assertError(api.post(sale, path(booked.id(), "changes"), change(booked, "call 0905123456", later, 60,
				null, null, null, null)), 400, "VALIDATION_FAILED", "reason");
		// survey-schedule spec A-14: a number pasted with no-break spaces is found as well
		Map<String, Object> pasted = change(booked, "Later", later, 60, null, null, null, null);
		pasted.put("accessNote", "Gọi 0905 123 456");
		assertError(api.post(sale, path(booked.id(), "changes"), pasted), 400, "VALIDATION_FAILED", "accessNote");
	}

	@Test
	void finishedAppointments_refuseEveryCommand_andLeaveTheOverlapList() throws Exception {
		TestUser surveyor = survey.user("Command Lone Sale", "SALE");
		Booked cancelledOne = booked(surveyor, true);
		Booked visitedOne = booked(surveyor, false);
		Result cancelled = api.post(surveyor, path(cancelledOne.id(), "cancellation"), cancel(cancelledOne,
				"STAFF_CANCELLED", null));
		assertEquals(200, cancelled.status(), cancelled.rawBody());
		survey.shiftBack(visitedOne.id(), Duration.ofDays(2));
		Result visited = api.post(surveyor, path(visitedOne.id(), "visit"), visit(visitedOne,
				visitedOne.start().minus(Duration.ofDays(2)), "Visited", surveyor, null));
		assertEquals(200, visited.status(), visited.rawBody());

		for (Booked done : List.of(cancelledOne, visitedOne)) {
			Result refused = api.post(surveyor, path(done.id(), "changes"), change(done, "Later",
					done.start().plus(Duration.ofHours(1)), 60, null, null, null, null));
			assertError(refused, 409, "INVALID_STATE_TRANSITION", "status");
			assertError(api.post(surveyor, path(done.id(), "cancellation"), cancel(done, "STAFF_CANCELLED", null)),
					409, "INVALID_STATE_TRANSITION", "status");
		}
		String otherLead = survey.lead(leader, surveyor, "CONTACTED");
		Result precheck = api.get(surveyor, SurveyApiTestSupport.LEADS + "/" + otherLead + "/survey-overlaps?startAt="
				+ cancelledOne.start() + "&durationMinutes=60");
		Result precheckPast = api.get(surveyor, SurveyApiTestSupport.LEADS + "/" + otherLead
				+ "/survey-overlaps?startAt=" + visitedOne.start().minus(Duration.ofDays(2)) + "&durationMinutes=60");
		assertEquals(0, precheck.body().path("overlaps").size(), precheck.rawBody());
		assertEquals(0, precheckPast.body().path("overlaps").size(), precheckPast.rawBody());
	}

	// ==================== W15-10: confirmation ====================

	@Test
	void confirmation_recordsActorTimeAndNote_andTheSameValueIsANoOp() throws Exception {
		Booked booked = booked(false);

		Result confirmed = api.post(leader, path(booked.id(), "confirmation"),
				confirmation(booked, true, "Owner agreed by phone"));
		assertEquals(200, confirmed.status(), confirmed.rawBody());
		JsonNode confirmation = appointmentOf(confirmed, booked.id()).path("confirmation");
		assertEquals("CONFIRMED", confirmation.path("state").asString());
		assertEquals(leader.userId(), confirmation.path("changedByUserId").asString());
		assertEquals("Command Leader", confirmation.path("changedByName").asString());
		assertEquals("Owner agreed by phone", confirmation.path("note").asString());
		assertEquals("SURVEY_CONFIRMED", confirmed.body().path("historyEntry").path("type").asString());

		long leadVersion = survey.version(booked.leadId());
		Result again = api.post(sale, path(booked.id(), "confirmation"), Map.of("expectedLeadVersion", leadVersion,
				"expectedAppointmentVersion", survey.appointmentVersion(booked.id()), "confirmed", true));
		assertEquals(200, again.status(), again.rawBody());
		assertTrue(again.body().path("historyEntry").isNull());
		assertEquals(leadVersion, again.body().path("lead").path("version").asLong());

		Result removed = api.post(sale, path(booked.id(), "confirmation"), Map.of("expectedLeadVersion", leadVersion,
				"expectedAppointmentVersion", survey.appointmentVersion(booked.id()), "confirmed", false));
		JsonNode unconfirmed = appointmentOf(removed, booked.id()).path("confirmation");
		assertEquals("USER", unconfirmed.path("cause").asString());
		assertTrue(unconfirmed.path("everConfirmed").asBoolean());
		assertError(api.post(sale, path(booked.id(), "confirmation"), confirmation(booked, true, "0905 123 456")),
				400, "VALIDATION_FAILED", "note");
	}

	// ==================== W15-11: cancel ====================

	@Test
	void cancel_firstSurvey_movesTheLeadBackToContacted_andOtherNeedsADescription() throws Exception {
		Booked booked = booked(false);

		assertError(api.post(sale, path(booked.id(), "cancellation"), cancel(booked, "OTHER", null)), 400,
				"VALIDATION_FAILED", "description");
		assertError(api.post(sale, path(booked.id(), "cancellation"), cancel(booked, "LEAD_CLOSED", null)), 400,
				"VALIDATION_FAILED", "cancelReason");
		assertError(api.post(sale, path(booked.id(), "cancellation"), cancel(booked, "OTHER", "gọi 0905 123 456")),
				400, "VALIDATION_FAILED", "description");
		Result cancelled = api.post(sale, path(booked.id(), "cancellation"), cancel(booked, "OWNER_ABSENT",
				"Waited 30 minutes"));

		assertEquals(200, cancelled.status(), cancelled.rawBody());
		assertEquals("CONTACTED", cancelled.body().path("lead").path("status").asString());
		JsonNode cancellation = appointmentOf(cancelled, booked.id()).path("cancellation");
		assertEquals("OWNER_ABSENT", cancellation.path("cancelReason").asString());
		assertEquals("Waited 30 minutes", cancellation.path("description").asString());
		assertEquals("Command Sale", cancellation.path("cancelledByName").asString());

		Result again = survey.book(sale, booked.leadId(), survey.booking(booked.leadId(),
				SurveyApiTestSupport.startIn(30)));
		assertEquals(201, again.status(), again.rawBody());
		assertEquals("FIRST", again.body().path("survey").path("appointments").path(0).path("kind").asString());
	}

	@Test
	void cancel_overdueAppointment_isAllowed() throws Exception {
		Booked booked = booked(false);
		survey.shiftBack(booked.id(), Duration.ofDays(3));

		Result cancelled = api.post(sale, path(booked.id(), "cancellation"), cancel(booked, "OWNER_ABSENT", null));

		assertEquals(200, cancelled.status(), cancelled.rawBody());
	}

	/** survey-schedule E-5: a cancelled re-survey keeps the current visit, so the lead stays SURVEY_SCHEDULED. */
	@Test
	void cancel_resurveyWithACurrentVisit_keepsSurveyScheduled() throws Exception {
		Booked first = visitedLead();
		Map<String, Object> body = survey.booking(first.leadId(), SurveyApiTestSupport.startIn(30));
		body.put("resurveyReason", "Owner added a floor");
		Result resurvey = survey.book(sale, first.leadId(), body);
		assertEquals(201, resurvey.status(), resurvey.rawBody());
		JsonNode created = resurvey.body().path("survey").path("appointments").path(0);
		assertEquals("RESURVEY", created.path("kind").asString());
		assertEquals(first.id(), created.path("previousVisitId").asString());
		assertTrue(resurvey.body().path("historyEntry").path("toStatus").isNull());

		String id = created.path("id").asString();
		Result cancelled = api.post(sale, path(id, "cancellation"), Map.of("expectedLeadVersion",
				survey.version(first.leadId()), "expectedAppointmentVersion", survey.appointmentVersion(id),
				"cancelReason", "OWNER_CANCELLED"));

		assertEquals(200, cancelled.status(), cancelled.rawBody());
		assertEquals("SURVEY_SCHEDULED", cancelled.body().path("lead").path("status").asString());
	}

	// ==================== W15-20 ====================

	@Test
	void resurvey_fromContactedWithACurrentVisit_linksTheVisitAndNeedsAReason() throws Exception {
		Booked first = visitedLead();
		assertEquals(200, survey.moveTo(leader, first.leadId(), "ON_HOLD").status());
		assertEquals(200, survey.moveTo(leader, first.leadId(), "CONTACTED").status());

		Result withoutReason = survey.book(sale, first.leadId(), survey.booking(first.leadId(),
				SurveyApiTestSupport.startIn(30)));
		assertError(withoutReason, 400, "VALIDATION_FAILED", "resurveyReason");

		Map<String, Object> body = survey.booking(first.leadId(), SurveyApiTestSupport.startIn(30));
		body.put("resurveyReason", "Owner rebuilt the kitchen");
		Result resurvey = survey.book(sale, first.leadId(), body);

		assertEquals(201, resurvey.status(), resurvey.rawBody());
		assertEquals("RESURVEY", resurvey.body().path("survey").path("appointments").path(0).path("kind").asString());
		assertEquals("SURVEY_SCHEDULED", resurvey.body().path("lead").path("status").asString());
	}

	// ==================== W15-16, W15-19: own visit ====================

	@Test
	void ownVisit_enteredLater_keepsArrivalAndRecordingTime() throws Exception {
		Booked booked = booked(false);
		survey.shiftBack(booked.id(), Duration.ofDays(2));
		Instant start = booked.start().minus(Duration.ofDays(2));
		Instant arrived = start.minus(Duration.ofMinutes(30));

		assertError(api.post(sale, path(booked.id(), "visit"), visit(booked, start.minus(Duration.ofMinutes(61)),
				"Too early", sale, null)), 400, "VALIDATION_FAILED", "arrivedAt");
		assertError(api.post(sale, path(booked.id(), "visit"), visit(booked, Instant.now().truncatedTo(
				ChronoUnit.MINUTES).plus(Duration.ofMinutes(2)), "Future", sale, null)), 400, "VALIDATION_FAILED",
				"arrivedAt");
		assertError(api.post(sale, path(booked.id(), "visit"), visit(booked, start.minus(Duration.ofDays(5)),
				"Before booking", sale, null)), 400, "VALIDATION_FAILED", "arrivedAt");
		assertError(api.post(sale, path(booked.id(), "visit"), visit(booked, arrived, " ", sale, null)), 400,
				"VALIDATION_FAILED", "note");
		assertError(api.post(sale, path(booked.id(), "visit"), visit(booked, arrived, "call 0905 123 456", sale,
				null)), 400, "VALIDATION_FAILED", "note");
		Result visited = api.post(sale, path(booked.id(), "visit"), visit(booked, arrived, "Met the owner", sale,
				null));

		assertEquals(200, visited.status(), visited.rawBody());
		JsonNode visit = appointmentOf(visited, booked.id()).path("visit");
		assertEquals("OWN", visit.path("recordKind").asString());
		assertEquals(arrived, Instant.parse(visit.path("arrivedAt").asString()));
		assertEquals(1, visit.path("revisionNo").asInt());
		Instant recordedAt = Instant.parse(visit.path("recordedAt").asString());
		// The arrival lies 23 to 24.5 hours back (startIn(24) shifted back two days, minus 30 minutes)
		assertTrue(recordedAt.isAfter(arrived.plus(Duration.ofHours(12))));
		assertEquals("SURVEY_SCHEDULED", visited.body().path("lead").path("status").asString());
		assertEquals(recordedAt, Instant.parse(visited.body().path("historyEntry").path("occurredAt").asString()));
		assertEquals("SURVEY_VISITED", visited.body().path("historyEntry").path("type").asString());
	}

	@Test
	void ownVisit_inTheBookingMinute_isAccepted() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		Instant start = Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(Duration.ofMinutes(30));
		String id = survey.booked(sale, leadId, start);
		Instant bookingMinute = jdbc.queryForObject("select created_at from survey_appointments where id = ?",
				java.sql.Timestamp.class, id).toInstant().truncatedTo(ChronoUnit.MINUTES);

		Result visited = api.post(sale, path(id, "visit"), visit(new Booked(leadId, id, start, survey.version(leadId)),
				bookingMinute, "Already on site", sale, null));

		assertEquals(200, visited.status(), visited.rawBody());
	}

	@Test
	void visit_afterALaterEdit_usesTheRevisionInForceAtTheArrival() throws Exception {
		Booked[] booked = new Booked[3];
		for (int i = 0; i < 3; i++) {
			booked[i] = editedAndShifted();
		}
		// Revision 1 starts at S, revision 2 (start S + 2h) is in force from S - 30 min
		Instant[] arrivals = {
				booked[0].start().minus(Duration.ofMinutes(50)),
				booked[1].start().minus(Duration.ofMinutes(20)),
				booked[2].start().plus(Duration.ofMinutes(90))};

		Result underRevisionOne = api.post(sale, path(booked[0].id(), "visit"), visit(booked[0], arrivals[0],
				"Before the edit", sale, null));
		Result tooEarlyForRevisionTwo = api.post(sale, path(booked[1].id(), "visit"), visit(booked[1], arrivals[1],
				"After the edit", sale, null));
		Result underRevisionTwo = api.post(sale, path(booked[2].id(), "visit"), visit(booked[2], arrivals[2],
				"Near the new start", sale, null));

		assertEquals(200, underRevisionOne.status(), underRevisionOne.rawBody());
		assertEquals(1, appointmentOf(underRevisionOne, booked[0].id()).path("visit").path("revisionNo").asInt());
		assertError(tooEarlyForRevisionTwo, 400, "VALIDATION_FAILED", "arrivedAt");
		assertEquals(200, underRevisionTwo.status(), underRevisionTwo.rawBody());
		assertEquals(2, appointmentOf(underRevisionTwo, booked[2].id()).path("visit").path("revisionNo").asInt());
	}

	// ==================== W15-17: proxy visit ====================

	@Test
	void proxyVisit_byAManager_needsReasonAndBasis() throws Exception {
		Booked booked = booked(false);
		survey.shiftBack(booked.id(), Duration.ofDays(2));
		Instant arrived = booked.start().minus(Duration.ofDays(2));
		LocalDate arrivalDate = SurveyTimeRules.vietnamDate(arrived);

		assertError(api.post(leader, path(booked.id(), "visit"), visit(booked, arrived, "Visited", sale, null)), 400,
				"VALIDATION_FAILED", "proxy");
		assertError(api.post(leader, path(booked.id(), "visit"), visit(booked, arrived, "Visited", sale,
				proxy("Sale had no signal", arrivalDate.minusDays(1), "Sale called me"))), 400, "VALIDATION_FAILED",
				"proxy.basis.confirmedOn");
		assertError(api.post(leader, path(booked.id(), "visit"), visit(booked, arrived, "Visited", sale,
				proxy("call 0905 123 456", arrivalDate, "Sale called me"))), 400, "VALIDATION_FAILED",
				"proxy.reason");
		assertError(api.post(leader, path(booked.id(), "visit"), visit(booked, arrived, "Visited", sale,
				proxy("No signal", arrivalDate, "0905 123 456"))), 400, "VALIDATION_FAILED", "proxy.basis.text");
		assertError(api.post(leader, path(booked.id(), "visit"), visit(booked, arrived, "Visited", otherSale,
				proxy("No signal", arrivalDate, "Sale called me"))), 422, "PERFORMER_MISMATCH", "performedByUserId");

		Result recorded = api.post(leader, path(booked.id(), "visit"), visit(booked, arrived, "Visited", sale,
				proxy("No signal", arrivalDate, "Sale called me")));

		assertEquals(200, recorded.status(), recorded.rawBody());
		JsonNode visit = appointmentOf(recorded, booked.id()).path("visit");
		assertEquals("PROXY", visit.path("recordKind").asString());
		assertEquals(leader.userId(), visit.path("recordedByUserId").asString());
		assertEquals("No signal", visit.path("proxy").path("reason").asString());
		assertEquals(arrivalDate.toString(), visit.path("proxy").path("basis").path("confirmedOn").asString());
	}

	@Test
	void proxyVisit_byASale_isForbidden() throws Exception {
		Booked booked = booked(false);
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);

		Result result = api.post(sale, path(booked.id(), "visit"), visit(booked, booked.start(), "Visited", sale,
				proxy("No signal", today, "Called")));
		Result forSomeoneElse = api.post(sale, path(booked.id(), "visit"), visit(booked, booked.start(), "Visited",
				otherSale, null));

		assertEquals(403, result.status(), result.rawBody());
		assertEquals(403, forSomeoneElse.status(), forSomeoneElse.rawBody());
	}

	// ==================== W15-21: resume ====================

	@Test
	void resume_heldLeadWithACurrentVisit_goesBackToSurveyScheduled() throws Exception {
		Booked first = visitedLead();
		assertEquals(200, survey.moveTo(leader, first.leadId(), "ON_HOLD").status());
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);
		String resumptions = SurveyApiTestSupport.LEADS + "/" + first.leadId() + "/survey-resumptions";

		assertError(api.post(leader, resumptions, resume(first.leadId(), "Back", today.minusDays(1), "Old talk")),
				400, "VALIDATION_FAILED", "basis.confirmedOn");
		assertError(api.post(leader, resumptions, resume(first.leadId(), "call 0905 123 456", today, "Scope same")),
				400, "VALIDATION_FAILED", "reason");
		Result resumed = api.post(leader, resumptions, resume(first.leadId(), "Owner is back", today,
				"Requirement and scope have not changed"));

		assertEquals(201, resumed.status(), resumed.rawBody());
		assertEquals("SURVEY_SCHEDULED", resumed.body().path("lead").path("status").asString());
		assertEquals("SURVEY_RESUMED", resumed.body().path("historyEntry").path("type").asString());
		assertEquals(first.id(), resumed.body().path("historyEntry").path("referenceId").asString());
		JsonNode resumption = resumed.body().path("survey").path("resumptions").path(0);
		assertEquals(first.id(), resumption.path("visitAppointmentId").asString());
		assertEquals("Owner is back", resumption.path("reason").asString());
		assertEquals("Command Leader", resumption.path("actorName").asString());
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_resumptions where lead_id = ?",
				Integer.class, first.leadId()));
	}

	@Test
	void resume_withoutCurrentVisitOrNotHeld_isRefused() throws Exception {
		String held = survey.lead(leader, sale, "ON_HOLD");
		String contacted = survey.lead(leader, sale, "CONTACTED");
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);

		Result noVisit = api.post(leader, SurveyApiTestSupport.LEADS + "/" + held + "/survey-resumptions",
				resume(held, "Back", today, "Same scope"));
		Result notHeld = api.post(leader, SurveyApiTestSupport.LEADS + "/" + contacted + "/survey-resumptions",
				resume(contacted, "Back", today, "Same scope"));

		assertEquals(409, noVisit.status(), noVisit.rawBody());
		assertEquals("NO_CURRENT_VISIT", noVisit.body().path("code").asString());
		assertError(notHeld, 409, "INVALID_STATE_TRANSITION", "status");
		assertEquals("ON_HOLD", notHeld.body().path("errors").path(0).path("allowedValues").path(0).asString());
	}

	// ==================== W15-24, W15-26, W15-27 ====================

	@Test
	void commandsOnAnotherSalesOrAGuessedAppointment_areNotFound() throws Exception {
		Booked booked = booked(false);
		String guessedMessage = null;
		for (String id : List.of(booked.id(), GUESSED_ID, "x")) {
			List<Result> refused = List.of(
					api.post(otherSale, path(id, "changes"), change(booked, "Later",
							booked.start().plus(Duration.ofHours(1)), 60, null, null, null, null)),
					api.post(otherSale, path(id, "confirmation"), confirmation(booked, true, null)),
					api.post(otherSale, path(id, "cancellation"), cancel(booked, "STAFF_CANCELLED", null)),
					// An own record of the caller without proxy: the same 404, never the 400 on proxy that would tell
					// the appointment exists
					api.post(otherSale, path(id, "visit"), visit(booked, booked.start(), "Visited", otherSale, null)));
			for (Result result : refused) {
				assertError(result, 404, "NOT_FOUND", null);
				assertTrue(!result.rawBody().contains(booked.id()), "the id is never echoed");
				if (guessedMessage == null) {
					guessedMessage = result.body().path("message").asString();
				}
				assertEquals(guessedMessage, result.body().path("message").asString());
			}
		}
		assertError(api.post(otherSale, SurveyApiTestSupport.LEADS + "/" + booked.leadId()
				+ "/survey-resumptions", resume(booked.leadId(), "Back", LocalDate.now(SurveyTimeRules.ZONE),
				"Same")), 404, "NOT_FOUND", null);
	}

	@Test
	void everyCommandAnswer_writesOneAccessLogRow() throws Exception {
		Booked booked = booked(false);
		int before = survey.accessLogRows(booked.leadId());

		api.post(sale, path(booked.id(), "confirmation"), confirmation(booked, true, null));
		api.post(sale, path(booked.id(), "confirmation"), Map.of("expectedLeadVersion",
				survey.version(booked.leadId()), "expectedAppointmentVersion", survey.appointmentVersion(booked.id()),
				"confirmed", true));

		assertEquals(before + 2, survey.accessLogRows(booked.leadId()));
		assertEquals(null, jdbc.queryForObject("select note from owner_lead_activities where lead_id = ? and type = "
				+ "'SURVEY_CONFIRMED'", String.class, booked.leadId()));
	}

	@Test
	void ineligibleSurveyor_blocksChangeAndConfirm_butNotCancelOrProxyVisit() throws Exception {
		TestUser leaving = survey.user("Command Leaving", "SALE");
		Booked booked = booked(leaving, false);
		Booked overdue = booked(leaving, false);
		survey.shiftBack(overdue.id(), Duration.ofDays(2));
		Instant arrived = overdue.start().minus(Duration.ofDays(2));
		jdbc.update("delete from user_roles where user_id = ?", leaving.userId());

		assertError(api.post(leader, path(booked.id(), "changes"), change(booked, "Later",
				booked.start().plus(Duration.ofHours(1)), 60, null, null, null, null)), 422, "SURVEYOR_INELIGIBLE",
				null);
		assertError(api.post(leader, path(booked.id(), "confirmation"), confirmation(booked, true, null)), 422,
				"SURVEYOR_INELIGIBLE", null);
		Result cancelled = api.post(leader, path(booked.id(), "cancellation"), cancel(booked, "STAFF_CANCELLED",
				null));
		assertEquals(200, cancelled.status(), cancelled.rawBody());

		// survey-schedule BR-6, D-AI-20: a proxy record may name a performer who can no longer take leads
		Result proxyVisit = api.post(leader, path(overdue.id(), "visit"), visit(overdue, arrived, "Visited",
				leaving, proxy("Leaving sale finished the visit", SurveyTimeRules.vietnamDate(arrived), "Called")));
		assertEquals(200, proxyVisit.status(), proxyVisit.rawBody());
	}

	// ==================== helpers ====================

	/** A booked appointment as the tests know it: lead, id, start and the lead version after the booking. */
	private record Booked(String leadId, String id, Instant start, long leadVersion) {
	}

	private Booked booked(boolean ownerConfirmed) throws Exception {
		return booked(sale, ownerConfirmed);
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
				start, survey.version(leadId));
	}

	private Booked bookedConfirmed() throws Exception {
		return booked(true);
	}

	/** A lead with one visited appointment (booked, moved two days back and recorded by its surveyor). */
	private Booked visitedLead() throws Exception {
		Booked booked = booked(false);
		survey.shiftBack(booked.id(), Duration.ofDays(2));
		Instant start = booked.start().minus(Duration.ofDays(2));
		Result visited = api.post(sale, path(booked.id(), "visit"), visit(booked, start, "Visited", sale, null));
		assertEquals(200, visited.status(), visited.rawBody());
		return new Booked(booked.leadId(), booked.id(), start, survey.version(booked.leadId()));
	}

	/**
	 * Booked at start S, changed to start S + 2h (revision 2), moved two days back; revision 2 then takes effect
	 * 30 minutes before the shifted S. The answer's start is the shifted S.
	 */
	private Booked editedAndShifted() throws Exception {
		Booked booked = booked(false);
		Result changed = api.post(sale, path(booked.id(), "changes"), change(booked, "Two hours later",
				booked.start().plus(Duration.ofHours(2)), 60, null, null, null, null));
		assertEquals(200, changed.status(), changed.rawBody());
		survey.shiftBack(booked.id(), Duration.ofDays(2));
		Instant start = booked.start().minus(Duration.ofDays(2));
		jdbc.update("update survey_appointment_revisions set valid_from = ? where appointment_id = ? and "
				+ "revision_no = 2", java.sql.Timestamp.from(start.minus(Duration.ofMinutes(30))), booked.id());
		return new Booked(booked.leadId(), booked.id(), start, survey.version(booked.leadId()));
	}

	private static String path(String appointmentId, String action) {
		return APPOINTMENTS + "/" + appointmentId + "/" + action;
	}

	/** A full change body; a null place means the lead's own place, read from the lead row. */
	private Map<String, Object> change(Booked booked, String reason, Instant start, int duration,
			Map<String, Object> placeOrNull, String accessNote, String contactName, String contactRole) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(booked.leadId()));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(booked.id()));
		body.put("reason", reason);
		body.put("startAt", start.toString());
		body.put("durationMinutes", duration);
		body.put("meetingLocation", placeOrNull == null ? leadPlace(booked.leadId()) : placeOrNull);
		body.put("accessNote", accessNote);
		body.put("onSiteContactName", contactName);
		body.put("onSiteContactRole", contactRole);
		return body;
	}

	private Map<String, Object> confirmation(Booked booked, boolean confirmed, String note) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(booked.leadId()));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(booked.id()));
		body.put("confirmed", confirmed);
		body.put("note", note);
		return body;
	}

	private Map<String, Object> cancel(Booked booked, String reason, String description) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(booked.leadId()));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(booked.id()));
		body.put("cancelReason", reason);
		body.put("description", description);
		return body;
	}

	private Map<String, Object> visit(Booked booked, Instant arrivedAt, String note, TestUser performer,
			Map<String, Object> proxy) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(booked.leadId()));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(booked.id()));
		body.put("arrivedAt", arrivedAt.toString());
		body.put("note", note);
		body.put("performedByUserId", performer.userId());
		if (proxy != null) {
			body.put("proxy", proxy);
		}
		return body;
	}

	private static Map<String, Object> proxy(String reason, LocalDate confirmedOn, String text) {
		return Map.of("reason", reason, "basis", Map.of("confirmedOn", confirmedOn.toString(), "text", text));
	}

	private Map<String, Object> resume(String leadId, String reason, LocalDate confirmedOn, String text) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(leadId));
		body.put("reason", reason);
		body.put("basis", Map.of("confirmedOn", confirmedOn.toString(), "text", text));
		return body;
	}

	/** The lead's own place as a meeting place body. */
	private Map<String, Object> leadPlace(String leadId) {
		Map<String, Object> row = jdbc.queryForMap("select address_detail, province_code, ward_code, latitude, "
				+ "longitude from owner_leads where id = ?", leadId);
		Map<String, Object> place = new LinkedHashMap<>();
		place.put("addressText", row.get("address_detail"));
		place.put("provinceCode", row.get("province_code"));
		place.put("wardCode", row.get("ward_code"));
		place.put("latitude", row.get("latitude"));
		place.put("longitude", row.get("longitude"));
		return place;
	}

	private static Map<String, Object> place(String address, String ward) {
		Map<String, Object> place = new LinkedHashMap<>();
		place.put("addressText", address);
		place.put("provinceCode", "48");
		place.put("wardCode", ward);
		place.put("latitude", null);
		place.put("longitude", null);
		return place;
	}

	private static JsonNode appointmentOf(Result result, String id) {
		for (JsonNode appointment : result.body().path("survey").path("appointments")) {
			if (id.equals(appointment.path("id").asString())) {
				return appointment;
			}
		}
		throw new AssertionError("no appointment " + id + " in " + result.rawBody());
	}

	private static void assertError(Result result, int status, String code, String fieldOrNull) {
		assertEquals(status, result.status(), result.rawBody());
		assertEquals(code, result.body().path("code").asString(), result.rawBody());
		if (fieldOrNull == null) {
			assertEquals(0, result.body().path("errors").size(), result.rawBody());
		} else {
			assertEquals(fieldOrNull, result.body().path("errors").path(0).path("field").asString(),
					result.rawBody());
		}
	}
}
