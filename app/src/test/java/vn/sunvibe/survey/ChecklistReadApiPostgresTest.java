package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * The checklist reads, the survey block's checklist state, the access log and the security events end to end
 * (survey-checklist spec 4.10, 7.2; W16-19, W16-20, W16-21, A-10). Needs Docker.
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
class ChecklistReadApiPostgresTest {

	private static final String GUESSED_ID = "01JZZZZZZZZZZZZZZZZZZZZZZZ";
	private static final List<String> START_STATE_FIELDS = List.of("allowed", "visitAppointmentId",
			"intakeDeclarationRequired", "blockedBy");
	private static final List<String> SUMMARY_FIELDS = List.of("id", "checklistNo", "status", "editable",
			"readOnlyReason", "missingCount", "holderUserId", "holderName", "updatedAt", "updatedByUserId",
			"updatedByName");

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
	private TestUser saleB;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser outsider;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0963");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Checklist Read Sale", "SALE");
		saleB = survey.user("Checklist Read Sale B", "SALE");
		otherSale = survey.user("Checklist Read Other Sale", "SALE");
		leader = survey.user("Checklist Read Leader", "SALE_LEADER");
		outsider = survey.user("Checklist Read Outsider", "CUSTOMER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W16-19 ====================

	@Test
	void block_checklistStart_followsTheVisitAndTheViewer() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String scheduled = survey.booked(sale, leadId, SurveyApiTestSupport.startIn(24));

		JsonNode beforeVisit = block(sale, leadId);
		assertEquals(START_STATE_FIELDS, AcquisitionApiTestSupport.fieldNames(beforeVisit.path("checklistStart")));
		assertStart(beforeVisit, false, null, false, "NO_CURRENT_VISIT");
		assertEquals(0, SurveyApiTestSupport.appointmentOf(beforeVisit, scheduled).path("checklists").size());

		Visit visit = checklists.visited(leader, sale);
		assertStart(block(sale, visit.leadId()), true, visit.appointmentId(), false, null);
		assertStart(block(leader, visit.leadId()), false, null, false, "NOT_ASSIGNEE");

		String id = checklists.started(sale, visit.appointmentId());

		JsonNode afterStart = block(sale, visit.leadId());
		assertStart(afterStart, false, null, false, "ALREADY_STARTED");
		JsonNode summary = SurveyApiTestSupport.appointmentOf(afterStart, visit.appointmentId()).path("checklists")
				.path(0);
		assertEquals(SUMMARY_FIELDS, AcquisitionApiTestSupport.fieldNames(summary));
		assertEquals(id, summary.path("id").asString());
		assertEquals(1, summary.path("checklistNo").asInt());
		assertEquals("DRAFT", summary.path("status").asString());
		assertTrue(summary.path("editable").asBoolean());
		assertTrue(summary.path("readOnlyReason").isNull());
		assertTrue(summary.path("missingCount").asInt() > 0);
		assertEquals(sale.userId(), summary.path("holderUserId").asString());
		assertEquals("Checklist Read Sale", summary.path("holderName").asString());
		assertTrue(summary.path("updatedByUserId").isNull());
		JsonNode asLeader = SurveyApiTestSupport.appointmentOf(block(leader, visit.leadId()), visit.appointmentId())
				.path("checklists").path(0);
		assertFalse(asLeader.path("editable").asBoolean());
		assertEquals("NOT_ASSIGNEE", asLeader.path("readOnlyReason").asString());
	}

	@Test
	void block_checklistStart_asksANewAssigneeForTheIntake() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		checklists.reassign(leader, visit.leadId(), saleB);

		assertStart(block(saleB, visit.leadId()), true, visit.appointmentId(), true, null);
	}

	@Test
	void booking_cancelAndResume_withADraft_giveTheW15Answers() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		SurveyApiTestSupport.ok(survey.moveTo(leader, visit.leadId(), "ON_HOLD"));
		SurveyApiTestSupport.ok(survey.moveTo(leader, visit.leadId(), "CONTACTED"));

		Map<String, Object> booking = survey.booking(visit.leadId(), SurveyApiTestSupport.startIn(30));
		booking.put("resurveyReason", "Owner rebuilt the kitchen");
		Result booked = survey.book(sale, visit.leadId(), booking);
		assertEquals(201, booked.status(), booked.rawBody());
		assertEquals("SURVEY_SCHEDULED", booked.body().path("lead").path("status").asString());
		String resurvey = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		Map<String, Object> cancel = new LinkedHashMap<>();
		cancel.put("expectedLeadVersion", survey.version(visit.leadId()));
		cancel.put("expectedAppointmentVersion", survey.appointmentVersion(resurvey));
		cancel.put("cancelReason", "STAFF_CANCELLED");
		cancel.put("description", null);
		Result cancelled = api.post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + resurvey + "/cancellation",
				cancel);
		assertEquals(200, cancelled.status(), cancelled.rawBody());
		assertEquals("SURVEY_SCHEDULED", cancelled.body().path("lead").path("status").asString());
		SurveyApiTestSupport.ok(survey.moveTo(leader, visit.leadId(), "ON_HOLD"));
		Map<String, Object> resume = new LinkedHashMap<>();
		resume.put("expectedLeadVersion", survey.version(visit.leadId()));
		resume.put("reason", "Owner is back");
		resume.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		Result resumed = api.post(sale, SurveyApiTestSupport.LEADS + "/" + visit.leadId() + "/survey-resumptions",
				resume);

		assertEquals(201, resumed.status(), resumed.rawBody());
		assertEquals("SURVEY_SCHEDULED", resumed.body().path("lead").path("status").asString());
		assertTrue(checklists.readOk(sale, id).body().path("editable").asBoolean());
	}

	// ==================== W16-21 ====================

	@Test
	void refusalsOnExistingResources_writeOneSecurityEventEach_withTheCorrelationId() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		Visit notStarted = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String checkpointId = id + ":7";

		List<Result> refused = List.of(
				api.get(otherSale, SurveyApiTestSupport.LEADS + "/" + visit.leadId() + "/survey"),
				checklists.start(otherSale, visit.appointmentId(), null),
				checklists.read(otherSale, id),
				checklists.takeOver(otherSale, id, visit),
				checklists.checkpoint(otherSale, id, 7),
				checklists.start(leader, notStarted.appointmentId(), null),
				checklists.takeOver(leader, id, visit));
		List<Integer> statuses = refused.stream().map(Result::status).toList();
		assertEquals(List.of(404, 404, 404, 404, 404, 403, 403), statuses);

		List<Map<String, Object>> events = new ArrayList<>(checklists.securityEvents(visit.leadId()));
		events.addAll(checklists.securityEvents(notStarted.leadId()));
		List<String> seen = events.stream().map(event -> event.get("user_id").equals(otherSale.userId())
				? "other:" + event.get("resource_type") + ":" + event.get("resource_id") + ":" + event.get("action")
						+ ":" + event.get("outcome")
				: "leader:" + event.get("resource_type") + ":" + event.get("resource_id") + ":" + event.get("action")
						+ ":" + event.get("outcome")).toList();
		assertEquals(List.of(
				"other:LEAD:" + visit.leadId() + ":READ:NOT_VISIBLE",
				"other:APPOINTMENT:" + visit.appointmentId() + ":START:NOT_VISIBLE",
				"other:CHECKLIST:" + id + ":READ:NOT_VISIBLE",
				"other:CHECKLIST:" + id + ":TAKEOVER:NOT_VISIBLE",
				"other:CHECKPOINT:" + checkpointId + ":READ:NOT_VISIBLE",
				"leader:CHECKLIST:" + id + ":TAKEOVER:NOT_ASSIGNEE",
				"leader:APPOINTMENT:" + notStarted.appointmentId() + ":START:NOT_ASSIGNEE"), seen);
		List<String> correlationIds = refused.stream().map(result -> result.header(CorrelationId.HEADER)).toList();
		for (Map<String, Object> event : events) {
			assertTrue(correlationIds.contains((String) event.get("correlation_id")), event.toString());
		}
	}

	@Test
	void unknownIds_authFailures_conflictsAndIneligibility_writeNoSecurityEvent() throws Exception {
		TestUser leaving = survey.user("Checklist Read Leaving", "SALE");
		TestUser stranger = survey.user("Checklist Read Stranger", "SALE");
		Visit visit = checklists.visited(leader, sale);
		Visit ofLeaving = checklists.visited(leader, leaving);
		String id = checklists.started(sale, visit.appointmentId());
		checklists.makeIneligible(leaving);

		assertEquals(404, api.get(stranger, SurveyApiTestSupport.LEADS + "/" + GUESSED_ID + "/survey").status());
		assertEquals(404, checklists.read(stranger, GUESSED_ID).status());
		assertEquals(404, checklists.start(stranger, GUESSED_ID, null).status());
		assertEquals(404, checklists.takeOver(stranger, GUESSED_ID, 0, "New assignee", visit.arrivalDate(),
				"Leader handed it over").status());
		assertEquals(404, checklists.checkpoint(stranger, GUESSED_ID, 1).status());
		assertEquals(401, checklists.read(null, id).status());
		assertEquals(403, checklists.read(outsider, id).status());
		assertEquals(409, checklists.start(sale, visit.appointmentId(), null).status());
		assertEquals(409, checklists.takeOver(sale, id, visit).status());
		assertEquals(422, checklists.start(leaving, ofLeaving.appointmentId(), null).status());

		assertEquals(List.of(), checklists.securityEvents(visit.leadId()));
		assertEquals(List.of(), checklists.securityEvents(ofLeaving.leadId()));
		assertEquals(0, checklists.rows("select count(*) from survey_security_events where user_id = ?",
				stranger.userId()));
	}

	// ==================== W16-20, A-10 ====================

	@Test
	void accessLog_hasOneRowPerReadAndCommandAnswer_withTheResourceType() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		int before = checklists.accessLogs(visit.leadId()).size();

		block(sale, visit.leadId());
		String id = checklists.started(sale, visit.appointmentId());
		checklists.readOk(sale, id);
		checklists.reassign(leader, visit.leadId(), saleB);
		assertEquals(201, checklists.takeOver(saleB, id, visit).status());
		assertEquals(200, checklists.checkpoint(saleB, id, 1).status());
		checklists.read(sale, id);

		List<String> rows = checklists.accessLogs(visit.leadId());
		assertEquals(List.of("SURVEY_BLOCK:" + visit.leadId(), "CHECKLIST:" + id, "CHECKLIST:" + id,
				"CHECKLIST:" + id, "CHECKPOINT:" + id + ":1"), rows.subList(before, rows.size()));
	}

	@Test
	void checklistTexts_neverReachLogsHistoryOrErrorBodies() throws Exception {
		Logger surveyLogger = (Logger) LoggerFactory.getLogger("vn.sunvibe");
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		surveyLogger.addAppender(logs);
		try {
			TestUser performer = survey.user("Checklist Read Performer", "SALE");
			Visit visit = checklists.visited(leader, performer);
			checklists.reassign(leader, visit.leadId(), saleB);
			Result refused = checklists.start(saleB, visit.appointmentId(), ChecklistApiTestSupport.intake(
					"Marker intake reason 0905 123 456", visit.arrivalDate(), "Marker intake basis"));
			Result started = checklists.start(saleB, visit.appointmentId(), ChecklistApiTestSupport.intake(
					"Marker intake reason", visit.arrivalDate(), "Marker intake basis"));
			String id = started.body().path("checklist").path("id").asString();
			checklists.reassign(leader, visit.leadId(), sale);
			Result takenOver = checklists.takeOver(sale, id, checklists.checklistVersion(id),
					"Marker takeover reason", visit.arrivalDate(), "Marker takeover basis");

			assertEquals(400, refused.status(), refused.rawBody());
			assertFalse(refused.rawBody().contains("Marker"), refused.rawBody());
			assertFalse(refused.rawBody().contains("0905"), refused.rawBody());
			assertEquals(201, started.status(), started.rawBody());
			assertEquals(201, takenOver.status(), takenOver.rawBody());
			assertFalse(started.body().path("historyEntry").toString().contains("Marker"));
			assertFalse(takenOver.body().path("historyEntry").toString().contains("Marker"));
			assertEquals(0, checklists.rows("select count(*) from owner_lead_activities where lead_id = ? and "
					+ "note like '%Marker%'", visit.leadId()));
			for (ILoggingEvent event : logs.list) {
				assertFalse(event.getFormattedMessage().contains("Marker"), event.getFormattedMessage());
			}
		} finally {
			surveyLogger.detachAppender(logs);
		}
	}

	// ==================== helpers ====================

	private JsonNode block(TestUser caller, String leadId) throws Exception {
		Result result = api.get(caller, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	private static void assertStart(JsonNode block, boolean allowed, String visitIdOrNull,
			boolean intakeDeclarationRequired, String blockedByOrNull) {
		JsonNode start = block.path("checklistStart");
		assertEquals(allowed, start.path("allowed").asBoolean(), start.toString());
		if (visitIdOrNull == null) {
			assertTrue(start.path("visitAppointmentId").isNull(), start.toString());
		} else {
			assertEquals(visitIdOrNull, start.path("visitAppointmentId").asString(), start.toString());
		}
		assertEquals(intakeDeclarationRequired, start.path("intakeDeclarationRequired").asBoolean(), start.toString());
		if (blockedByOrNull == null) {
			assertTrue(start.path("blockedBy").isNull(), start.toString());
		} else {
			assertEquals(blockedByOrNull, start.path("blockedBy").asString(), start.toString());
		}
	}
}
