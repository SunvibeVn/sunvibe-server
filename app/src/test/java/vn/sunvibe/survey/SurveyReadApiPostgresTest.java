package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import vn.sunvibe.survey.core.domain.policy.ListGroup;

/**
 * Who may read and book surveys, the survey list and the survey access log end to end (survey-schedule spec 7;
 * W15-24 for block, booking and pre-check, W15-25, W15-26). Needs Docker.
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
class SurveyReadApiPostgresTest {

	private static final String GUESSED_ID = "01JZZZZZZZZZZZZZZZZZZZZZZZ";
	private static final List<String> LIST_ITEM_FIELDS = List.of("id", "leadId", "requestCode", "ownerContactName",
			"wardName", "startAt", "durationMinutes", "surveyorUserId", "surveyorName", "confirmation", "status",
			"group", "kind", "cannotBePerformed");
	private static final List<String> BLOCK_FIELDS = List.of("leadId", "intentCycle", "booking", "currentVisitId",
			"resumeAllowed", "appointments", "resumptions", "checklistStart");
	private static final List<String> APPOINTMENT_FIELDS = List.of("id", "leadId", "appointmentNo", "kind",
			"previousVisitId", "resurveyReason", "intent", "intentCycle", "inCurrentCycle", "status", "startAt",
			"durationMinutes", "meetingLocation", "leadLocation", "locationReason", "accessNote", "onSiteContactName",
			"onSiteContactRole", "surveyorUserId", "surveyorName", "cannotBePerformed", "holders", "confirmation",
			"visit", "cancellation", "revisions", "events", "createdByUserId", "createdByName", "createdAt",
			"version", "checklists");

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
	private TestUser sale1;
	private TestUser sale2;
	private TestUser leader;
	private TestUser manager;
	private TestUser superAdmin;
	private final List<TestUser> outsiders = new ArrayList<>();

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0973");
		sale1 = survey.user("Reader Sale One", "SALE");
		sale2 = survey.user("Reader Sale Two", "SALE");
		leader = survey.user("Reader Leader", "SALE_LEADER");
		manager = survey.user("Reader Manager", "SALES_MANAGER");
		superAdmin = survey.user("Reader Super", "SUPER_ADMIN");
		for (String role : List.of("CUSTOMER", "OWNER", "LISTING_REVIEWER")) {
			outsiders.add(survey.user("Outsider " + role, role));
		}
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W15-24 ====================

	@Test
	void anotherSalesLeadOrAGuessedId_isNotFoundForASale() throws Exception {
		String leadOf2 = survey.lead(leader, sale2, "CONTACTED");

		for (String leadId : List.of(leadOf2, GUESSED_ID, "not-a-ulid")) {
			assertNotFound(api.get(sale1, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey"));
			assertNotFound(api.post(sale1, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-appointments",
					Map.of("expectedLeadVersion", 0, "startAt", SurveyApiTestSupport.startIn(24).toString())));
			assertNotFound(api.get(sale1, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-overlaps?startAt="
					+ SurveyApiTestSupport.startIn(24) + "&durationMinutes=60"));
		}
		assertEquals(0, survey.accessLogRows(leadOf2));
	}

	@Test
	void rolesOutsideTheStaff_areForbidden_andNoTokenIsUnauthorized() throws Exception {
		String leadId = survey.lead(leader, sale1, "CONTACTED");
		for (TestUser outsider : outsiders) {
			assertEquals(403, api.get(outsider, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey").status());
			assertEquals(403, survey.book(outsider, leadId, survey.booking(leadId,
					SurveyApiTestSupport.startIn(24))).status());
			assertEquals(403, api.get(outsider, SurveyApiTestSupport.APPOINTMENTS).status());
			assertEquals(403, api.get(outsider, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-overlaps"
					+ "?startAt=" + SurveyApiTestSupport.startIn(24) + "&durationMinutes=60").status());
		}
		assertEquals(401, api.get(null, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey").status());
		assertEquals(401, api.get(null, SurveyApiTestSupport.APPOINTMENTS).status());
	}

	@Test
	void superAdmin_actsAsAdmin() throws Exception {
		String leadId = survey.lead(leader, sale1, "CONTACTED");

		Result booked = survey.book(superAdmin, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(24)));
		Result block = api.get(superAdmin, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");

		assertEquals(201, booked.status(), booked.rawBody());
		assertEquals(200, block.status(), block.rawBody());
	}

	// ==================== W15-26 ====================

	@Test
	void blockReadsAndBookingAnswers_writeOneAccessLogRowEach_listAndPrecheckWriteNone() throws Exception {
		String leadId = survey.lead(leader, sale1, "CONTACTED");

		Result block = api.get(sale1, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");
		assertEquals(200, block.status(), block.rawBody());
		assertEquals(BLOCK_FIELDS, AcquisitionApiTestSupport.fieldNames(block.body()));
		assertEquals(1, survey.accessLogRows(leadId));

		survey.booked(sale1, leadId, SurveyApiTestSupport.startIn(24));
		assertEquals(2, survey.accessLogRows(leadId));

		api.get(sale1, SurveyApiTestSupport.APPOINTMENTS);
		api.get(sale1, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-overlaps?startAt="
				+ SurveyApiTestSupport.startIn(24) + "&durationMinutes=60");
		assertEquals(2, survey.accessLogRows(leadId));

		Result again = api.get(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");
		assertEquals(APPOINTMENT_FIELDS,
				AcquisitionApiTestSupport.fieldNames(again.body().path("appointments").path(0)));
		assertEquals(3, survey.accessLogRows(leadId));
		assertEquals(leader.userId(), jdbc.queryForObject("select user_id from survey_access_logs where lead_id = ? "
				+ "order by accessed_at desc, id desc limit 1", String.class, leadId));
	}

	// ==================== W15-25 ====================

	@Test
	void list_saleSeesOnlyOwnScheduled_withTheSpecFields() throws Exception {
		String own = survey.lead(leader, sale1, "CONTACTED");
		String other = survey.lead(leader, sale2, "CONTACTED");
		String ownId = survey.booked(sale1, own, SurveyApiTestSupport.startIn(30));
		survey.booked(sale2, other, SurveyApiTestSupport.startIn(30));

		Result mine = api.get(sale1, SurveyApiTestSupport.APPOINTMENTS);

		assertEquals(200, mine.status(), mine.rawBody());
		List<String> ids = ids(mine.body().path("items"));
		assertTrue(ids.contains(ownId));
		JsonNode item = itemOf(mine.body().path("items"), ownId);
		assertEquals(LIST_ITEM_FIELDS, AcquisitionApiTestSupport.fieldNames(item));
		assertEquals(own, item.path("leadId").asString());
		assertEquals("Hải Châu", item.path("wardName").asString());
		assertEquals("Nguyễn Thị Lan", item.path("ownerContactName").asString());
		assertEquals("Reader Sale One", item.path("surveyorName").asString());
		assertEquals(List.of("state", "cause", "everConfirmed"),
				AcquisitionApiTestSupport.fieldNames(item.path("confirmation")));
		assertEquals("SCHEDULED", item.path("status").asString());
		for (JsonNode each : mine.body().path("items")) {
			assertEquals(sale1.userId(), each.path("surveyorUserId").asString());
		}
		assertEquals(List.of("items", "page", "size", "totalElements"),
				AcquisitionApiTestSupport.fieldNames(mine.body()));
	}

	@Test
	void list_scopeRules() throws Exception {
		assertEquals(403, api.get(sale1, SurveyApiTestSupport.APPOINTMENTS + "?scope=all").status());
		Result filterWithMine = api.get(leader, SurveyApiTestSupport.APPOINTMENTS + "?scope=mine&surveyorUserId="
				+ sale1.userId());
		assertEquals(400, filterWithMine.status(), filterWithMine.rawBody());
		assertEquals("surveyorUserId", filterWithMine.body().path("errors").path(0).path("field").asString());
		Result badScope = api.get(leader, SurveyApiTestSupport.APPOINTMENTS + "?scope=everyone");
		assertEquals(400, badScope.status());
		assertEquals("scope", badScope.body().path("errors").path(0).path("field").asString());
		assertEquals(400, api.get(leader, SurveyApiTestSupport.APPOINTMENTS + "?scope=all&size=101").status());
		assertEquals(400, api.get(leader, SurveyApiTestSupport.APPOINTMENTS + "?scope=all&page=-1").status());
	}

	@Test
	void list_managerAllWithSurveyorFilter_ordersByGroupStartAndId() throws Exception {
		TestUser sale3 = survey.user("Reader Sale Three", "SALE");
		String overdueLead = survey.lead(leader, sale3, "CONTACTED");
		String laterLead = survey.lead(leader, sale3, "CONTACTED");
		String soonLead = survey.lead(leader, sale3, "CONTACTED");
		String overdue = survey.booked(sale3, overdueLead, SurveyApiTestSupport.startIn(2));
		survey.shiftBack(overdue, Duration.ofHours(30));
		Instant laterStart = SurveyApiTestSupport.startIn(72);
		String later = survey.booked(sale3, laterLead, laterStart);
		Instant soonStart = SurveyApiTestSupport.startIn(0);
		String soon = survey.booked(sale3, soonLead, soonStart);

		Result page = api.get(manager, SurveyApiTestSupport.APPOINTMENTS + "?scope=all&surveyorUserId="
				+ sale3.userId());

		assertEquals(200, page.status(), page.rawBody());
		assertEquals(List.of(overdue, soon, later), ids(page.body().path("items")));
		assertEquals(3, page.body().path("totalElements").asLong());
		assertEquals("OVERDUE", itemOf(page.body().path("items"), overdue).path("group").asString());
		Instant now = Instant.now();
		assertEquals(ListGroup.of(soonStart, 60, now).name(),
				itemOf(page.body().path("items"), soon).path("group").asString());
		assertEquals("UPCOMING", itemOf(page.body().path("items"), later).path("group").asString());

		Result firstPageOfTwo = api.get(manager, SurveyApiTestSupport.APPOINTMENTS + "?scope=all&surveyorUserId="
				+ sale3.userId() + "&size=2&page=1");
		assertEquals(List.of(later), ids(firstPageOfTwo.body().path("items")));
	}

	@Test
	void list_flagsAppointmentsThatCannotBePerformed() throws Exception {
		TestUser leaving = survey.user("Reader Leaving", "SALE");
		String leadId = survey.lead(leader, leaving, "CONTACTED");
		String id = survey.booked(leaving, leadId, SurveyApiTestSupport.startIn(10));
		jdbc.update("delete from user_roles where user_id = ?", leaving.userId());

		Result page = api.get(leader, SurveyApiTestSupport.APPOINTMENTS + "?scope=all&surveyorUserId="
				+ leaving.userId());
		Result block = api.get(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey");

		assertTrue(itemOf(page.body().path("items"), id).path("cannotBePerformed").asBoolean());
		assertTrue(block.body().path("appointments").path(0).path("cannotBePerformed").asBoolean());
	}

	// ==================== helpers ====================

	private static void assertNotFound(Result result) {
		assertEquals(404, result.status(), result.rawBody());
		assertEquals("NOT_FOUND", result.body().path("code").asString());
	}

	private static List<String> ids(JsonNode items) {
		List<String> ids = new ArrayList<>();
		items.forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	private static JsonNode itemOf(JsonNode items, String id) {
		for (JsonNode item : items) {
			if (id.equals(item.path("id").asString())) {
				return item;
			}
		}
		throw new AssertionError("no item " + id);
	}
}
