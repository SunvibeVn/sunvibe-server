package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.fieldNames;
import static vn.sunvibe.survey.SurveyApiTestSupport.appointmentOf;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;
import static vn.sunvibe.survey.SurveyApiTestSupport.ok;

import java.math.BigDecimal;
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
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.SurveyApiTestSupport;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * The requirement change end to end (survey-schedule US-5, spec 7.1): W15-22 for the endpoint, W15-20 and W15-23 for
 * the bookings after it, and the detail's {@code intentChanges[]} (spec 7.2). Leads come from
 * {@link SurveyApiTestSupport}. Needs Docker.
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
class OwnerLeadIntentChangeApiPostgresTest {

	private static final String LEADS = SurveyApiTestSupport.LEADS;
	private static final List<String> SUMMARY_FIELDS = List.of("id", "requestCode", "status", "intent", "contactName",
			"provinceCode", "wardCode", "wardName", "propertyType", "assignedUserId", "assignedUserName",
			"nextActionAt", "possibleDuplicate", "createdAt", "lastActivityAt", "version");

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
		survey = new SurveyApiTestSupport(api, jdbc, "0976");
		sale = survey.user("Intent Sale", "SALE");
		otherSale = survey.user("Intent Other Sale", "SALE");
		leader = survey.user("Intent Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W15-22 ====================

	@Test
	void changeIntent_fromAssigned_movesToContacted_andKeepsTheChange() throws Exception {
		String leadId = survey.lead(leader, sale, "ASSIGNED");
		long version = survey.version(leadId);
		String snapshot = snapshot(leadId);
		int consents = jdbc.queryForObject("select count(*) from consent_records", Integer.class);
		LocalDate today = today();

		Result changed = api.post(sale, path(leadId), body(version, "SHORT_TERM_RENT", nightPrice(800_000),
				"Owner prefers short stays", today, "Owner said so on the phone"));

		ok(changed);
		assertEquals(SUMMARY_FIELDS, fieldNames(changed.body()));
		assertEquals("CONTACTED", changed.body().path("status").asString());
		assertEquals("SHORT_TERM_RENT", changed.body().path("intent").asString());
		assertEquals(version + 1, changed.body().path("version").asLong());
		Map<String, Object> lead = jdbc.queryForMap("select intent, intent_cycle, asking_price_amount, "
				+ "asking_price_unit from owner_leads where id = ?", leadId);
		assertEquals(2, lead.get("intent_cycle"));
		assertEquals(0, new BigDecimal("800000").compareTo((BigDecimal) lead.get("asking_price_amount")));
		assertEquals("NIGHT", lead.get("asking_price_unit"));
		assertEquals(snapshot, snapshot(leadId));
		assertEquals(consents, jdbc.queryForObject("select count(*) from consent_records", Integer.class));

		Map<String, Object> row = jdbc.queryForMap("select * from owner_lead_intent_changes where lead_id = ?", leadId);
		assertEquals("LONG_TERM_RENT", row.get("from_intent"));
		assertEquals("SHORT_TERM_RENT", row.get("to_intent"));
		assertEquals(0, new BigDecimal("12000000").compareTo((BigDecimal) row.get("from_price_amount")));
		assertEquals("MONTH", row.get("from_price_unit"));
		assertEquals("NIGHT", row.get("to_price_unit"));
		assertEquals("Owner prefers short stays", row.get("reason"));
		assertEquals(today, ((java.sql.Date) row.get("basis_confirmed_on")).toLocalDate());
		assertEquals("Owner said so on the phone", row.get("basis_text"));
		assertEquals(sale.userId(), row.get("actor_user_id"));
		assertEquals(2, row.get("new_intent_cycle"));
		JsonNode entry = latestActivity(leadId);
		assertEquals("INTENT_CHANGED", entry.path("type").asString());
		assertEquals("ASSIGNED", entry.path("fromStatus").asString());
		assertEquals("CONTACTED", entry.path("toStatus").asString());
		assertTrue(entry.path("referenceId").isNull());
	}

	@Test
	void changeIntent_fromContactedOrOnHold_keepsTheStatus() throws Exception {
		for (String status : List.of("CONTACTED", "ON_HOLD")) {
			String leadId = survey.lead(leader, sale, status);

			Result changed = api.post(leader, path(leadId), body(survey.version(leadId), "SHORT_TERM_RENT", null,
					"Owner prefers short stays", today(), "Owner said so"));

			ok(changed);
			assertEquals(status, changed.body().path("status").asString());
			JsonNode entry = latestActivity(leadId);
			assertEquals("INTENT_CHANGED", entry.path("type").asString());
			assertTrue(entry.path("fromStatus").isNull());
			assertTrue(entry.path("toStatus").isNull());
			assertTrue(jdbc.queryForObject("select asking_price_amount is null from owner_leads where id = ?",
					Boolean.class, leadId));
		}
	}

	@Test
	void changeIntent_fromSurveyScheduled_cancelsTheAppointmentAndMovesToContacted() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String appointmentId = survey.booked(sale, leadId, SurveyApiTestSupport.startIn(24));

		Result changed = api.post(sale, path(leadId), body(survey.version(leadId), "SHORT_TERM_RENT", null,
				"Owner prefers short stays", today(), "Owner said so"));

		ok(changed);
		assertEquals("CONTACTED", changed.body().path("status").asString());
		JsonNode appointment = appointmentOf(api.get(sale, LEADS + "/" + leadId + "/survey").body(), appointmentId);
		assertEquals("CANCELLED", appointment.path("status").asString());
		assertEquals("INTENT_CHANGED", appointment.path("cancellation").path("cancelReason").asString());
		assertTrue(appointment.path("cancellation").path("description").isNull());
		JsonNode entry = latestActivity(leadId);
		assertEquals("INTENT_CHANGED", entry.path("type").asString());
		assertEquals("SURVEY_SCHEDULED", entry.path("fromStatus").asString());
		assertEquals(appointmentId, entry.path("referenceId").asString());
		assertEquals(1, entry.path("referenceNo").asInt());
	}

	@Test
	void changeIntent_refusedValues_changeNothing() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		long version = survey.version(leadId);
		LocalDate created = SurveyTimeRules.vietnamDate(jdbc.queryForObject(
				"select created_at from owner_leads where id = ?", java.sql.Timestamp.class, leadId).toInstant());

		assertError(post(leadId, body(version, "SALE", null, "Sell instead", today(), "Owner said so")), 400,
				"VALIDATION_FAILED", "newIntent");
		assertError(post(leadId, body(version, "LONG_TERM_RENT", null, "Same", today(), "Owner said so")), 400,
				"VALIDATION_FAILED", "newIntent");
		assertError(post(leadId, body(version, "SHORT_TERM_RENT", AcquisitionApiTestSupport.price(500_000, "MONTH"),
				"Short", today(), "Owner said so")), 400, "VALIDATION_FAILED", "askingPrice.unit");
		Map<String, Object> halfDong = new LinkedHashMap<>(nightPrice(1));
		halfDong.put("amount", new BigDecimal("1500.5"));
		assertError(post(leadId, body(version, "SHORT_TERM_RENT", halfDong, "Short", today(), "Owner said so")), 400,
				"VALIDATION_FAILED", "askingPrice.amount");
		assertError(post(leadId, body(version, "SHORT_TERM_RENT", null, "Short", created.minusDays(1),
				"Owner said so")), 400, "VALIDATION_FAILED", "basis.confirmedOn");
		assertError(post(leadId, body(version, "SHORT_TERM_RENT", null, "Short", today().plusDays(1),
				"Owner said so")), 400, "VALIDATION_FAILED", "basis.confirmedOn");
		assertError(post(leadId, body(version, "SHORT_TERM_RENT", null, "gọi 0905 123 456", today(),
				"Owner said so")), 400, "VALIDATION_FAILED", "reason");
		assertError(post(leadId, body(version, "SHORT_TERM_RENT", null, "Short", today(), " ")), 400,
				"VALIDATION_FAILED", "basis.text");
		assertError(post(leadId, body(version - 1, "SHORT_TERM_RENT", null, "Short", today(), "Owner said so")),
				409, "CONCURRENT_MODIFICATION", "expectedVersion");

		Map<String, Object> missingPrice = body(version, "SHORT_TERM_RENT", null, "Short", today(), "Owner said so");
		missingPrice.remove("askingPrice");
		Result missing = post(leadId, missingPrice);
		assertError(missing, 400, "VALIDATION_FAILED", "askingPrice");
		assertEquals("askingPrice is required.", missing.body().path("errors").path(0).path("message").asString());

		assertEquals(version, survey.version(leadId));
		assertEquals("LONG_TERM_RENT", jdbc.queryForObject("select intent from owner_leads where id = ?",
				String.class, leadId));
		assertEquals(0, jdbc.queryForObject("select count(*) from owner_lead_intent_changes where lead_id = ?",
				Integer.class, leadId));
	}

	@Test
	void changeIntent_fromNewOrClosed_isRefusedWithTheAllowedStatuses() throws Exception {
		for (String leadId : List.of(survey.lead(leader, null, "NEW"), survey.lead(leader, sale, "REJECTED"))) {
			Result refused = api.post(leader, path(leadId), body(survey.version(leadId), "SHORT_TERM_RENT", null,
					"Short", today(), "Owner said so"));

			assertError(refused, 409, "INVALID_STATE_TRANSITION", "status");
			JsonNode error = refused.body().path("errors").path(0);
			assertEquals("This action is not allowed in the lead's current status.", error.path("message").asString());
			List<String> allowed = new ArrayList<>();
			error.path("allowedValues").forEach(value -> allowed.add(value.asString()));
			assertEquals(List.of("ASSIGNED", "CONTACTED", "ON_HOLD", "SURVEY_SCHEDULED"), allowed);
		}
	}

	@Test
	void changeIntent_callers_followTheStatusChangeRoles() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		TestUser customer = survey.user("Intent Customer", "CUSTOMER");
		Map<String, Object> body = body(survey.version(leadId), "SHORT_TERM_RENT", null, "Short", today(),
				"Owner said so");

		assertEquals(401, api.post(null, path(leadId), body).status());
		assertEquals(403, api.post(customer, path(leadId), body).status());
		assertError(api.post(otherSale, path(leadId), body), 404, "NOT_FOUND", null);
		assertEquals(0, jdbc.queryForObject("select count(*) from owner_lead_intent_changes where lead_id = ?",
				Integer.class, leadId));
	}

	// ==================== W15-20, W15-23 and the detail ====================

	@Test
	void afterARequirementChange_theNextBookingIsFirst() throws Exception {
		Visited visited = visitedLead();

		ok(api.post(sale, path(visited.leadId()), body(survey.version(visited.leadId()), "SHORT_TERM_RENT", null,
				"Owner prefers short stays", today(), "Owner said so")));
		JsonNode block = api.get(sale, LEADS + "/" + visited.leadId() + "/survey").body();
		assertTrue(block.path("currentVisitId").isNull(), block.toString());
		Result booked = survey.book(sale, visited.leadId(), survey.booking(visited.leadId(),
				SurveyApiTestSupport.startIn(30)));

		assertEquals(201, booked.status(), booked.rawBody());
		JsonNode created = booked.body().path("survey").path("appointments").path(0);
		assertEquals("FIRST", created.path("kind").asString());
		assertTrue(created.path("previousVisitId").isNull());
		assertEquals(2, booked.body().path("survey").path("intentCycle").asInt());
	}

	@Test
	void longShortLong_theLastCycleStartsAgainFromAFirstSurvey() throws Exception {
		Visited visited = visitedLead();
		String leadId = visited.leadId();
		ok(api.post(sale, path(leadId), body(survey.version(leadId), "SHORT_TERM_RENT", null, "Short stays",
				today(), "Owner said so")));
		String shortTerm = survey.booked(sale, leadId, SurveyApiTestSupport.startIn(30));

		ok(api.post(leader, path(leadId), body(survey.version(leadId), "LONG_TERM_RENT",
				AcquisitionApiTestSupport.price(10_000_000, "MONTH"), "Back to long stays", today(),
				"Owner called back")));
		Result again = survey.book(sale, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(32)));

		assertEquals(201, again.status(), again.rawBody());
		JsonNode block = again.body().path("survey");
		assertEquals(3, block.path("intentCycle").asInt());
		assertEquals("FIRST", block.path("appointments").path(0).path("kind").asString());
		assertTrue(block.path("currentVisitId").isNull(), block.toString());
		assertEquals("INTENT_CHANGED", appointmentOf(block, shortTerm).path("cancellation").path("cancelReason")
				.asString());
		assertEquals("VISITED", appointmentOf(block, visited.id()).path("status").asString());

		JsonNode changes = api.get(leader, LEADS + "/" + leadId).body().path("intentChanges");
		assertEquals(2, changes.size());
		JsonNode first = changes.path(0);
		assertEquals(List.of("fromIntent", "toIntent", "fromAskingPrice", "toAskingPrice", "reason", "basis",
				"actorUserId", "actorName", "newIntentCycle", "occurredAt"), fieldNames(first));
		assertEquals("LONG_TERM_RENT", first.path("fromIntent").asString());
		assertEquals("SHORT_TERM_RENT", first.path("toIntent").asString());
		assertEquals(12_000_000L, first.path("fromAskingPrice").path("amount").asLong());
		assertEquals("VND", first.path("fromAskingPrice").path("currency").asString());
		assertEquals("MONTH", first.path("fromAskingPrice").path("unit").asString());
		assertTrue(first.path("toAskingPrice").isNull());
		assertEquals("Short stays", first.path("reason").asString());
		assertEquals(today().toString(), first.path("basis").path("confirmedOn").asString());
		assertEquals("Owner said so", first.path("basis").path("text").asString());
		assertEquals("Intent Sale", first.path("actorName").asString());
		assertEquals(2, first.path("newIntentCycle").asInt());
		JsonNode second = changes.path(1);
		assertEquals("Intent Leader", second.path("actorName").asString());
		assertEquals(3, second.path("newIntentCycle").asInt());
		assertFalse(Instant.parse(second.path("occurredAt").asString())
				.isBefore(Instant.parse(first.path("occurredAt").asString())));
	}

	// ==================== helpers ====================

	private record Visited(String leadId, String id) {
	}

	/** A long-term lead of the sale with one visited appointment (booked, moved two days back and recorded). */
	private Visited visitedLead() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		Instant start = SurveyApiTestSupport.startIn(24);
		String id = survey.booked(sale, leadId, start);
		survey.shiftBack(id, Duration.ofDays(2));
		Map<String, Object> visit = new LinkedHashMap<>();
		visit.put("expectedLeadVersion", survey.version(leadId));
		visit.put("expectedAppointmentVersion", survey.appointmentVersion(id));
		visit.put("arrivedAt", start.minus(Duration.ofDays(2)).toString());
		visit.put("note", "Visited");
		visit.put("performedByUserId", sale.userId());
		ok(api.post(sale, SurveyApiTestSupport.APPOINTMENTS + "/" + id + "/visit", visit));
		return new Visited(leadId, id);
	}

	private Result post(String leadId, Map<String, Object> body) throws Exception {
		return api.post(sale, path(leadId), body);
	}

	private static String path(String leadId) {
		return LEADS + "/" + leadId + "/intent-changes";
	}

	/** A full body; a null price is sent as an explicit null. */
	private static Map<String, Object> body(long version, String newIntent, Map<String, Object> priceOrNull,
			String reason, LocalDate confirmedOn, String basisText) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", version);
		body.put("newIntent", newIntent);
		body.put("askingPrice", priceOrNull);
		body.put("reason", reason);
		body.put("basis", Map.of("confirmedOn", confirmedOn.toString(), "text", basisText));
		return body;
	}

	private static Map<String, Object> nightPrice(long amount) {
		return AcquisitionApiTestSupport.price(amount, "NIGHT");
	}

	private static LocalDate today() {
		return LocalDate.now(SurveyTimeRules.ZONE);
	}

	private String snapshot(String leadId) {
		return jdbc.queryForObject("select submission_snapshot from owner_leads where id = ?", String.class, leadId);
	}

	private JsonNode latestActivity(String leadId) throws Exception {
		JsonNode activities = api.get(leader, LEADS + "/" + leadId).body().path("activities");
		return activities.path(activities.size() - 1);
	}
}
