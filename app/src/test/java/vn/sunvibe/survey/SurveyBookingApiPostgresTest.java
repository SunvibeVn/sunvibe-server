package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
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

/**
 * {@code POST /api/v1/staff/owner-leads/{leadId}/survey-appointments} and the overlap pre-check end to end
 * (survey-schedule spec 7.1; W15-1, W15-2, W15-3, W15-6, W15-7 for the booking fields, W15-8, W15-27 for booking).
 * Needs Docker.
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
class SurveyBookingApiPostgresTest {

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
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0972");
		sale = survey.user("Survey Sale", "SALE");
		leader = survey.user("Survey Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W15-1 ====================

	@Test
	void book_ownContactedRentalLead_schedulesAndMovesTheLead() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		long version = survey.version(leadId);
		Instant start = SurveyApiTestSupport.startIn(24);

		Result result = survey.book(sale, leadId, survey.booking(leadId, start));

		assertEquals(201, result.status(), result.rawBody());
		JsonNode appointment = result.body().path("survey").path("appointments").path(0);
		assertEquals("SCHEDULED", appointment.path("status").asString());
		assertEquals(1, appointment.path("appointmentNo").asInt());
		assertEquals("FIRST", appointment.path("kind").asString());
		assertEquals(sale.userId(), appointment.path("surveyorUserId").asString());
		assertEquals("Survey Sale", appointment.path("surveyorName").asString());
		assertEquals(60, appointment.path("durationMinutes").asInt());
		assertEquals(start, Instant.parse(appointment.path("startAt").asString()));
		assertEquals("UNCONFIRMED", appointment.path("confirmation").path("state").asString());
		assertEquals("NEW", appointment.path("confirmation").path("cause").asString());
		JsonNode lead = result.body().path("lead");
		assertEquals("SURVEY_SCHEDULED", lead.path("status").asString());
		assertEquals(version + 1, lead.path("version").asLong());
		JsonNode entry = result.body().path("historyEntry");
		assertEquals("SURVEY_BOOKED", entry.path("type").asString());
		assertEquals("CONTACTED", entry.path("fromStatus").asString());
		assertEquals("SURVEY_SCHEDULED", entry.path("toStatus").asString());
		assertEquals(appointment.path("id").asString(), entry.path("referenceId").asString());
		assertEquals(1, entry.path("referenceNo").asInt());
		assertTrue(entry.path("note").isNull());
		assertEquals(List.of(), AcquisitionApiTestSupport.fieldNames(result.body().path("overlaps")));
		Map<String, Object> row = jdbc.queryForMap("select to_status, reference_id, reference_no, note from "
				+ "owner_lead_activities where lead_id = ? and type = 'SURVEY_BOOKED'", leadId);
		assertEquals("SURVEY_SCHEDULED", row.get("to_status"));
		assertEquals(appointment.path("id").asString(), row.get("reference_id"));
		assertEquals(1, row.get("reference_no"));
		assertEquals(null, row.get("note"));
	}

	@Test
	void book_fromAssigned_movesToSurveyScheduled() throws Exception {
		String leadId = survey.lead(leader, sale, "ASSIGNED");

		Result result = survey.book(leader, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(30)));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals("ASSIGNED", result.body().path("historyEntry").path("fromStatus").asString());
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(leadId));
	}

	// ==================== W15-2 (order), W15-27 ====================

	@Test
	void book_alreadyScheduled_isRefusedFirst() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		survey.booked(sale, leadId, SurveyApiTestSupport.startIn(24));

		Result again = survey.book(sale, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(48)));

		assertEquals(409, again.status(), again.rawBody());
		assertEquals("SURVEY_ALREADY_SCHEDULED", again.body().path("code").asString());
		assertEquals(0, again.body().path("errors").size());
	}

	@Test
	void book_newHeldOrClosedLead_isNotAllowedOnStatus() throws Exception {
		for (String status : List.of("NEW", "ON_HOLD", "REJECTED")) {
			String leadId = survey.lead(leader, "NEW".equals(status) ? null : sale, status);

			Result result = survey.book(leader, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(24)));

			assertEquals(409, result.status(), status + " " + result.rawBody());
			assertEquals("SURVEY_NOT_ALLOWED", result.body().path("code").asString());
			JsonNode error = result.body().path("errors").path(0);
			assertEquals("status", error.path("field").asString());
			assertEquals(List.of("ASSIGNED", "CONTACTED"), values(error.path("allowedValues")));
		}
	}

	@Test
	void book_saleIntent_isNotAllowedOnIntent() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED", "SALE",
				AcquisitionApiTestSupport.price(3_000_000_000L, "TOTAL"));

		Result result = survey.book(sale, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(24)));

		assertEquals(409, result.status(), result.rawBody());
		assertEquals("SURVEY_NOT_ALLOWED", result.body().path("code").asString());
		assertEquals("intent", result.body().path("errors").path(0).path("field").asString());
		assertEquals(List.of("SHORT_TERM_RENT", "LONG_TERM_RENT"),
				values(result.body().path("errors").path(0).path("allowedValues")));
	}

	@Test
	void book_assigneeNoLongerEligible_isSurveyorIneligible() throws Exception {
		TestUser leaving = survey.user("Leaving Sale", "SALE");
		String leadId = survey.lead(leader, leaving, "CONTACTED");
		jdbc.update("delete from user_roles where user_id = ?", leaving.userId());

		Result result = survey.book(leader, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(24)));

		assertEquals(422, result.status(), result.rawBody());
		assertEquals("SURVEYOR_INELIGIBLE", result.body().path("code").asString());
		assertEquals(0, result.body().path("errors").size());
		assertEquals("CONTACTED", survey.leadStatus(leadId));
	}

	// ==================== W15-3 ====================

	@Test
	void book_startOrDurationOutOfBounds_isRefusedOnTheFieldAndStoresNothing() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		Instant currentMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES);

		assertRefused(leadId, Map.of("startAt", currentMinute.minus(Duration.ofMinutes(2)).toString()), "startAt");
		assertRefused(leadId, Map.of("startAt", currentMinute.plus(Duration.ofDays(91)).toString()), "startAt");
		assertRefused(leadId, Map.of("startAt", SurveyApiTestSupport.startIn(5).plusSeconds(30).toString()),
				"startAt");
		for (int duration : new int[]{10, 495, 50}) {
			assertRefused(leadId, Map.of("durationMinutes", duration), "durationMinutes");
		}
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_appointments where lead_id = ?",
				Integer.class, leadId));
		assertEquals("CONTACTED", survey.leadStatus(leadId));
	}

	// ==================== W15-6 ====================

	@Test
	void book_withoutMeetingPlace_copiesTheLeadsPlace() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");

		Result result = survey.book(sale, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(24)));

		JsonNode appointment = result.body().path("survey").path("appointments").path(0);
		JsonNode meeting = appointment.path("meetingLocation");
		JsonNode leadPlace = appointment.path("leadLocation");
		assertEquals(leadPlace, meeting);
		assertEquals(SurveyApiTestSupport.WARD, meeting.path("wardCode").asString());
		assertEquals("Hải Châu", meeting.path("wardName").asString());
		assertEquals("Đà Nẵng", meeting.path("provinceName").asString());
		assertEquals(16.0678, meeting.path("latitude").asDouble(), 1e-9);
		assertTrue(appointment.path("locationReason").isNull());
	}

	@Test
	void book_otherMeetingPlace_needsAReasonAndValidCatalogValues() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");

		assertRefused(leadId, Map.of("meetingLocation", place("Cafe 3B", "48", SurveyApiTestSupport.OTHER_WARD,
				null, null)), "locationReason");
		assertRefused(leadId, Map.of("meetingLocation", place("Cafe 3B", "48", "99999", null, null),
				"locationReason", "Meet there"), "meetingLocation.wardCode");
		assertRefused(leadId, Map.of("meetingLocation", place("Cafe 3B", "01", SurveyApiTestSupport.WARD, null, null),
				"locationReason", "Meet there"), "meetingLocation.provinceCode");
		assertRefused(leadId, Map.of("meetingLocation", place("Cafe 3B", "48", SurveyApiTestSupport.WARD, 15.5,
				108.2), "locationReason", "Meet there"), "meetingLocation.latitude");
		assertRefused(leadId, Map.of("meetingLocation", place("Pier", "48", SurveyApiTestSupport.SPECIAL_ZONE_WARD,
				16.0, 108.2), "locationReason", "Meet there"), "meetingLocation.latitude");

		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(24));
		body.put("meetingLocation", place("Cafe 3B", "48", SurveyApiTestSupport.OTHER_WARD, 16.05, 108.21));
		body.put("locationReason", "Owner meets us at the cafe");
		Result result = survey.book(sale, leadId, body);

		assertEquals(201, result.status(), result.rawBody());
		JsonNode appointment = result.body().path("survey").path("appointments").path(0);
		assertEquals("Hòa Cường", appointment.path("meetingLocation").path("wardName").asString());
		assertEquals("Owner meets us at the cafe", appointment.path("locationReason").asString());
		assertEquals(SurveyApiTestSupport.WARD, jdbc.queryForObject("select ward_code from owner_leads where id = ?",
				String.class, leadId));
	}

	// ==================== W15-7 (booking fields) ====================

	@Test
	void book_freeTextWithAPhoneNumber_isRefusedOnThatField() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		for (String field : List.of("accessNote", "onSiteContactName", "onSiteContactRole", "confirmationNote",
				"resurveyReason")) {
			Map<String, Object> extra = new LinkedHashMap<>();
			extra.put(field, "gọi 0905 123 456");
			if (field.equals("confirmationNote")) {
				extra.put("ownerConfirmed", true);
			}
			assertRefused(leadId, extra, field);
		}
		for (String phone : List.of("+84.905.123.456", "(+84) 905 123 456", "0236 3812 345")) {
			assertRefused(leadId, Map.of("accessNote", phone), "accessNote");
		}
		assertRefused(leadId, Map.of("meetingLocation", place("0905 123 456", "48", SurveyApiTestSupport.OTHER_WARD,
				null, null), "locationReason", "Meet there"), "meetingLocation.addressText");
		assertRefused(leadId, Map.of("meetingLocation", place("Cafe", "48", SurveyApiTestSupport.OTHER_WARD, null,
				null), "locationReason", "call 0905123456"), "locationReason");

		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(24));
		body.put("accessNote", "tầng 12, căn 1203");
		body.put("onSiteContactName", "K12/5 Lê Duẩn");
		assertEquals(201, survey.book(sale, leadId, body).status());
	}

	// ==================== W15-8 ====================

	@Test
	void overlaps_areListedBeforeSaving_andTheSaveStillSucceeds() throws Exception {
		String first = survey.lead(leader, sale, "CONTACTED");
		String second = survey.lead(leader, sale, "CONTACTED");
		Instant start = SurveyApiTestSupport.startIn(60);
		String existing = survey.booked(sale, first, start);

		Result precheck = api.get(sale, SurveyApiTestSupport.LEADS + "/" + second + "/survey-overlaps?startAt="
				+ start.plus(Duration.ofMinutes(30)) + "&durationMinutes=60");
		Result booked = survey.book(sale, second, survey.booking(second, start.plus(Duration.ofMinutes(30))));

		assertEquals(200, precheck.status(), precheck.rawBody());
		JsonNode overlap = precheck.body().path("overlaps").path(0);
		assertEquals(existing, overlap.path("appointmentId").asString());
		assertEquals(first, overlap.path("leadId").asString());
		assertEquals(List.of("appointmentId", "leadId", "requestCode", "startAt", "durationMinutes"),
				AcquisitionApiTestSupport.fieldNames(overlap));
		assertEquals(201, booked.status(), booked.rawBody());
		assertEquals(existing, booked.body().path("overlaps").path(0).path("appointmentId").asString());
	}

	// ==================== helpers ====================

	private void assertRefused(String leadId, Map<String, Object> overrides, String field) throws Exception {
		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(24));
		body.putAll(overrides);

		Result result = survey.book(sale, leadId, body);

		assertEquals(400, result.status(), field + " " + result.rawBody());
		assertEquals("VALIDATION_FAILED", result.body().path("code").asString());
		assertEquals(field, result.body().path("errors").path(0).path("field").asString(), result.rawBody());
	}

	private static Map<String, Object> place(String address, String province, String ward, Double latitude,
			Double longitude) {
		Map<String, Object> place = new LinkedHashMap<>();
		place.put("addressText", address);
		place.put("provinceCode", province);
		place.put("wardCode", ward);
		place.put("latitude", latitude);
		place.put("longitude", longitude);
		return place;
	}

	private static List<String> values(JsonNode array) {
		List<String> values = new java.util.ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}
}
