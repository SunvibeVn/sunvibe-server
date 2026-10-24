package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
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
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Warnings of earlier hazards across chains of visits of one requirement (survey-checklist BR-18, BR-24, spec 4.6.1,
 * A-20; W16-43, W16-39), through real visits: each later visit is a W15 re-survey booked after a hold, moved back in
 * time and visited, with its own draft. Severity and status are stated for every link. Needs Docker.
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
class ChecklistEarlierHazardApiPostgresTest {

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
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0967");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Hazard Sale", "SALE");
		saleB = survey.user("Hazard Sale B", "SALE");
		leader = survey.user("Hazard Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W16-43 ====================

	@Test
	void aChainOfFourVisits_warnsForTheLatestUnaddressedHazard() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String h1 = checklists.newId();
		String first = draftOnNextVisit(leadId, 0);
		hazards(first, hazard(h1, "IMPORTANT", "OPEN", null));

		String second = draftOnNextVisit(leadId, 1);
		String h2 = checklists.newId();
		JsonNode carried = hazards(second, link(h2, first, h1, "IMPORTANT", "OPEN"));
		assertTrue(warnings(carried).contains("OPEN_IMPORTANT_HAZARD/" + h2), warnings(carried).toString());
		assertFalse(warnings(carried).contains("EARLIER_HAZARD_OPEN/" + h1), warnings(carried).toString());
		assertEquals(List.of(second + "/" + h2 + "/CARRIED"), addressedBy(carried, h1));

		String third = draftOnNextVisit(leadId, 2);
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h2), warnings(checklists.readOk(sale, third).body()));
		String h3 = checklists.newId();
		assertEquals(List.of(), earlier(hazards(third, link(h3, second, h2, "IMPORTANT", "OBSERVED_RESOLVED"))));
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h2), earlier(hazards(third, link(h3, second, h2, "MINOR",
				"OPEN"))));
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h2), earlier(hazards(third)));
		JsonNode importantOpen = hazards(third, link(h3, second, h2, "IMPORTANT", "OPEN"));
		assertEquals(List.of(), earlier(importantOpen));
		assertTrue(warnings(importantOpen).contains("OPEN_IMPORTANT_HAZARD/" + h3));
		assertEquals(List.of(), warnings(hazards(third, link(h3, second, h2, "MINOR", "OBSERVED_RESOLVED"))));
		ObjectNode reported = link(h3, second, h2, "IMPORTANT", "OWNER_REPORTED_FIXED");
		reported.put("ownerReportedOn", LocalDate.now(SurveyTimeRules.ZONE).toString());
		reported.put("receivedByUserId", sale.userId());
		JsonNode fixReport = hazards(third, reported);
		assertEquals(List.of("OPEN_IMPORTANT_HAZARD/" + h3), warnings(fixReport));
		hazards(third);

		String fourth = draftOnNextVisit(leadId, 3);
		String h4 = checklists.newId();
		JsonNode skipping = hazards(fourth, link(h4, first, h1, "IMPORTANT", "OBSERVED_RESOLVED"));
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h2), earlier(skipping));
		assertEquals(List.of(second + "/" + h2 + "/CARRIED", fourth + "/" + h4 + "/RESOLVED"),
				addressedBy(skipping, h1));
		String h5 = checklists.newId();
		assertEquals(List.of(), earlier(hazards(fourth, link(h4, first, h1, "IMPORTANT", "OBSERVED_RESOLVED"),
				link(h5, second, h2, "MINOR", "OBSERVED_RESOLVED"))));
		JsonNode firstAgain = checklists.readOk(sale, first).body();
		assertEquals("OPEN", firstAgain.path("groups").path("safety").path("content").path("hazards").path("value")
				.path(0).path("status").asString());
	}

	@Test
	void aMinorLink_keepsTheEarlierWarning_untilALaterResolvedLink() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String h1 = checklists.newId();
		String first = draftOnNextVisit(leadId, 0);
		hazards(first, hazard(h1, "IMPORTANT", "OPEN", null));

		String second = draftOnNextVisit(leadId, 1);
		String minor = checklists.newId();
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h1), earlier(hazards(second, link(minor, first, h1, "MINOR",
				"OPEN"))));
		ObjectNode minorFixed = link(minor, first, h1, "MINOR", "OWNER_REPORTED_FIXED");
		minorFixed.put("ownerReportedOn", LocalDate.now(SurveyTimeRules.ZONE).toString());
		minorFixed.put("receivedByUserId", sale.userId());
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h1), earlier(hazards(second, minorFixed)));
		hazards(second, link(minor, first, h1, "MINOR", "OPEN"));

		String third = draftOnNextVisit(leadId, 2);
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h1), earlier(checklists.readOk(sale, third).body()));
		String resolving = checklists.newId();
		assertEquals(List.of(), earlier(hazards(third, link(resolving, first, h1, "MINOR", "OBSERVED_RESOLVED"))));
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h1), earlier(hazards(third, link(resolving, first, h1,
				"MINOR", "OPEN"))));
	}

	@Test
	void aLinkedHazardIsLinkedOnce_andNeedsItsOwnDescription() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String h1 = checklists.newId();
		String first = draftOnNextVisit(leadId, 0);
		hazards(first, hazard(h1, "IMPORTANT", "OPEN", null));
		String second = draftOnNextVisit(leadId, 1);

		Result twice = save(second, link(checklists.newId(), first, h1, "MINOR", "OPEN"),
				link(checklists.newId(), first, h1, "MINOR", "OPEN"));
		ObjectNode empty = link(checklists.newId(), first, h1, "MINOR", "OPEN");
		empty.putNull("description");
		Result noDescription = save(second, empty);

		assertError(twice, 400, "VALIDATION_FAILED", "content.hazards.value.1.previous");
		assertError(noDescription, 400, "VALIDATION_FAILED", "content.hazards.value.0.description");
	}

	// ==================== W16-39 ====================

	@Test
	void aHazardOfAnEarlierRequirement_neverWarnsInTheNewOne_andCannotBeLinked() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String h1 = checklists.newId();
		String first = draftOnNextVisit(leadId, 0);
		hazards(first, hazard(h1, "IMPORTANT", "OPEN", null));
		changeIntent(leadId, "SHORT_TERM_RENT", null);
		changeIntent(leadId, "LONG_TERM_RENT", AcquisitionApiTestSupport.price(12_000_000, "MONTH"));

		String second = draftOnNextVisit(leadId, 1);
		JsonNode fresh = checklists.readOk(sale, second).body();

		assertEquals(List.of(), earlier(fresh));
		assertEquals(0, fresh.path("earlierHazards").size());
		assertError(save(second, link(checklists.newId(), first, h1, "IMPORTANT", "OPEN")), 400,
				"VALIDATION_FAILED", "content.hazards.value.0.previous");
	}

	// ==================== W16-47 (BR-30, spec A-29) ====================

	/**
	 * W16-47 whole: the same hazardId across removal. Checklist #1 holds H0 IMPORTANT OPEN and had H9 removed while it
	 * was a draft; on the current visit A saved L (linking H0), H1, H2 (with a fix report the leader, C, heard on day
	 * r) and H3. Every step is a separate request, so each tombstone is stored in and read from PostgreSQL.
	 */
	@Test
	void hazards_removedAndSentAgain_keepWhoIsProtected() throws Exception {
		LocalDate r = LocalDate.now(SurveyTimeRules.ZONE);
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String h0 = checklists.newId();
		String h9 = checklists.newId();
		String first = draftOnNextVisit(leadId, 0);
		hazards(first, hazard(h0, "IMPORTANT", "OPEN", null), hazard(h9, "MINOR", "OPEN", null));
		hazards(first, hazard(h0, "IMPORTANT", "OPEN", null));
		String second = draftOnNextVisit(leadId, 1);
		String l = checklists.newId();
		String h1 = checklists.newId();
		String h2 = checklists.newId();
		String h3 = checklists.newId();
		ObjectNode linkL = link(l, first, h0, "IMPORTANT", "OPEN");
		ObjectNode hazard1 = hazard(h1, "IMPORTANT", "OPEN", null);
		ObjectNode hazard2 = hazard(h2, "MINOR", "OWNER_REPORTED_FIXED", "Owner replaced the socket");
		hazard2.put("ownerReportedOn", r.toString());
		hazard2.put("receivedByUserId", leader.userId());
		hazard2.put("relayBasis", "The leader called the owner");
		ObjectNode hazard3 = hazard(h3, "MINOR", "OPEN", null);
		hazards(second, linkL, hazard1, hazard2, hazard3);

		// A's own H3 removed and put back: nobody else to protect
		hazards(second, linkL, hazard1, hazard2);
		hazards(second, linkL, hazard1, hazard2, hazard3);
		// H2 removed by A (C's report needs a correction) and sent back unchanged: C alone is protected
		assertCorrectionNeeded(sale, second, linkL, hazard1, hazard3);
		savedAndRetried(sale, second, fix(), linkL, hazard1, hazard3);
		JsonNode h2Tombstone = storedTombstones(second).path("safety/hazards/" + h2);
		assertEquals(sale.userId(), h2Tombstone.path("observedByUserId").asString());
		assertEquals(sale.userId(), h2Tombstone.path("enteredByUserId").asString());
		assertEquals(leader.userId(), h2Tombstone.path("ownerReport").path("receivedByUserId").asString());
		assertEquals(r.toString(), h2Tombstone.path("ownerReport").path("reportedOn").asString());
		assertCorrectionNeeded(sale, second, linkL, hazard1, hazard2, hazard3);

		SurveyApiTestSupport.ok(api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", saleB.userId(), "expectedVersion", survey.version(leadId))));
		takeOver(saleB, second);
		Result checkpoint = checklists.checkpoint(saleB, second, 1);
		assertEquals(200, checkpoint.status(), checkpoint.rawBody());
		assertWithoutReportFacts(List.of(l, h1, h3), checkpoint.body().path("snapshot"));
		assertWithoutReportFacts(List.of(l, h1, h3), checklists.readOk(saleB, second).body());

		// B removes H1, which A observed: its warning goes and its tombstone names A and B
		JsonNode withoutH1 = savedAndRetried(saleB, second, fix(), linkL, hazard3);
		assertFalse(warnings(withoutH1).contains("OPEN_IMPORTANT_HAZARD/" + h1), warnings(withoutH1).toString());
		JsonNode h1Tombstone = storedTombstones(second).path("safety/hazards/" + h1);
		assertEquals(sale.userId(), h1Tombstone.path("observedByUserId").asString());
		assertEquals(saleB.userId(), h1Tombstone.path("enteredByUserId").asString());
		ObjectNode otherH1 = hazard(h1, "MINOR", "OPEN", null);
		otherH1.put("description", "Railing loose again");
		assertCorrectionNeeded(saleB, second, linkL, otherH1, hazard3);
		ObjectNode reportedH1 = hazard(h1, "IMPORTANT", "OWNER_REPORTED_FIXED", "Owner says the railing is fixed");
		reportedH1.put("ownerReportedOn", r.toString());
		reportedH1.put("receivedByUserId", saleB.userId());
		assertCorrectionNeeded(saleB, second, linkL, reportedH1, hazard3);
		ObjectNode newH1 = hazard(h1, "IMPORTANT", "OPEN", null);
		newH1.put("description", "Railing loose at the stairs");
		JsonNode restored = savedAndRetried(saleB, second, fix(), linkL, newH1, hazard3);
		JsonNode sent = hazardOf(restored, h1);
		assertEquals("Railing loose at the stairs", sent.path("description").asString());
		assertEquals("OPEN", sent.path("status").asString());
		assertTrue(sent.path("ownerReportedOn").isNull(), sent.toString());
		assertTrue(warnings(restored).contains("OPEN_IMPORTANT_HAZARD/" + h1), warnings(restored).toString());

		// L removed brings H0's warning back; L sent again addresses H0 only while its content does
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h0), earlier(savedAndRetried(saleB, second, fix(), newH1,
				hazard3)));
		JsonNode linkedAgain = savedAndRetried(saleB, second, fix(), linkL, newH1, hazard3);
		assertEquals(List.of(), earlier(linkedAgain));
		assertEquals(List.of(second + "/" + l + "/CARRIED"), addressedBy(linkedAgain, h0));
		ObjectNode minorL = link(l, first, h0, "MINOR", "OPEN");
		assertEquals(List.of("EARLIER_HAZARD_OPEN/" + h0), earlier(savedAndRetried(saleB, second, fix(), minorL,
				newH1, hazard3)));

		// a new hazardId with H1's first description is a new entry; a tombstone is never a link target
		ObjectNode h4 = hazard(checklists.newId(), "IMPORTANT", "OPEN", null);
		savedAndRetried(saleB, second, null, minorL, newH1, hazard3, h4);
		assertError(saveAs(saleB, second, null, minorL, newH1, hazard3, h4, link(checklists.newId(), first, h9,
				"MINOR", "OPEN")), 400, "VALIDATION_FAILED", "content.hazards.value.4.previous");

		savedAndRetried(saleB, second, fix(), minorL, newH1, h4);
		SurveyApiTestSupport.ok(api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", sale.userId(), "expectedVersion", survey.version(leadId))));
		takeOver(sale, second);
		assertCorrectionNeeded(sale, second, minorL, newH1, h4, hazard3);
	}

	// ==================== helpers ====================

	/**
	 * Books the lead's next survey (a re-survey after a hold when it has a visit), moves it two days back and visits
	 * it, then starts the draft with a G1 of one space; visit {@code n} arrives later than visit {@code n - 1}.
	 */
	private String draftOnNextVisit(String leadId, int n) throws Exception {
		String status = survey.leadStatus(leadId);
		if ("SURVEY_SCHEDULED".equals(status)) {
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "ON_HOLD"));
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "CONTACTED"));
		}
		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(24 + 6 * n));
		if (n > 0 && hasVisit(leadId)) {
			body.put("resurveyReason", "Check the hazards again");
		}
		Result booked = survey.book(sale, leadId, body);
		assertEquals(201, booked.status(), booked.rawBody());
		String appointmentId = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		survey.shiftBack(appointmentId, Duration.ofDays(2));
		Instant arrivedAt = jdbc.queryForObject("select start_at from survey_appointments where id = ?",
				Timestamp.class, appointmentId).toInstant();
		Result visited = checklists.recordVisit(sale, leadId, appointmentId, arrivedAt);
		assertEquals(200, visited.status(), visited.rawBody());
		String id = checklists.started(sale, appointmentId);
		String spaceId = checklists.newId();
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		return id;
	}

	private boolean hasVisit(String leadId) {
		return checklists.rows("select count(*) from survey_appointments where lead_id = ? and status = 'VISITED' "
				+ "and intent_cycle = (select intent_cycle from owner_leads where id = ?)", leadId, leadId) > 0;
	}

	private JsonNode hazards(String checklistId, ObjectNode... hazards) throws Exception {
		Result result = save(checklistId, hazards);
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	private Result save(String checklistId, ObjectNode... hazards) throws Exception {
		return checklists.save(sale, checklistId, "safety", c -> set(c, "hazards", checklists.value(List.of(
				hazards))));
	}

	private Result saveAs(TestUser caller, String checklistId, Map<String, Object> correctionOrNull,
			ObjectNode... hazards) throws Exception {
		return checklists.save(caller, checklistId, "safety", correctionOrNull, c -> set(c, "hazards",
				checklists.value(List.of(hazards))));
	}

	private void assertCorrectionNeeded(TestUser caller, String checklistId, ObjectNode... hazards) throws Exception {
		assertError(saveAs(caller, checklistId, null, hazards), 400, "VALIDATION_FAILED", "correction");
	}

	/**
	 * Saves G5 through a read as the caller and at once sends the identical body again: the retry answers 200 as a
	 * no-op (spec A-17) with no save-log or History row, and the stored group, tombstones included, stays as it was.
	 */
	private JsonNode savedAndRetried(TestUser caller, String checklistId, Map<String, Object> correctionOrNull,
			ObjectNode... hazards) throws Exception {
		JsonNode read = checklists.readOk(caller, checklistId).body();
		ObjectNode content = ChecklistApiTestSupport.input(read, "safety");
		set(content, "hazards", checklists.value(List.of(hazards)));
		long version = ChecklistApiTestSupport.version(read, "safety");
		Result saved = checklists.put(caller, checklistId, "safety", version, content, correctionOrNull, null);
		assertEquals(200, saved.status(), saved.rawBody());
		int rows = checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?", checklistId);
		int history = checklists.rows("select count(*) from owner_lead_activities where reference_id = ?",
				checklistId);
		String stored = storedContent(checklistId);

		Result retried = checklists.put(caller, checklistId, "safety", version, content, correctionOrNull, null);

		assertEquals(200, retried.status(), retried.rawBody());
		assertEquals(rows, checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?",
				checklistId));
		assertEquals(history, checklists.rows("select count(*) from owner_lead_activities where reference_id = ?",
				checklistId));
		assertEquals(stored, storedContent(checklistId));
		return saved.body();
	}

	private Map<String, Object> fix() {
		return ChecklistApiTestSupport.correction("Hazards checked again", LocalDate.now(SurveyTimeRules.ZONE),
				"Called the owner");
	}

	private void takeOver(TestUser caller, String checklistId) throws Exception {
		Result takenOver = checklists.takeOver(caller, checklistId, checklists.checklistVersion(checklistId),
				"New assignee", LocalDate.now(SurveyTimeRules.ZONE), "Leader handed it over");
		assertEquals(201, takenOver.status(), takenOver.rawBody());
	}

	private String storedContent(String checklistId) {
		return jdbc.queryForObject("select content from survey_checklist_groups where checklist_id = ? and group_key = "
				+ "'SAFETY'", String.class, checklistId);
	}

	/** The tombstones of the stored G5 document (server-only, spec A-29), read from PostgreSQL. */
	private JsonNode storedTombstones(String checklistId) {
		return api.json().readTree(storedContent(checklistId)).path("tombstones");
	}

	/** The hazards of a checklist view (or a checkpoint's snapshot) are exactly these, none with report facts. */
	private static void assertWithoutReportFacts(List<String> hazardIds, JsonNode view) {
		List<String> ids = new ArrayList<>();
		for (JsonNode hazard : view.path("groups").path("safety").path("content").path("hazards").path("value")) {
			ids.add(hazard.path("hazardId").asString());
			assertTrue(hazard.path("ownerReportedOn").isNull(), hazard.toString());
			assertTrue(hazard.path("receivedByUserId").isNull(), hazard.toString());
		}
		assertEquals(hazardIds, ids);
	}

	private static JsonNode hazardOf(JsonNode checklist, String hazardId) {
		for (JsonNode hazard : checklist.path("groups").path("safety").path("content").path("hazards").path("value")) {
			if (hazardId.equals(hazard.path("hazardId").asString())) {
				return hazard;
			}
		}
		throw new AssertionError("no hazard " + hazardId);
	}

	private ObjectNode hazard(String hazardId, String severity, String status, String statusNoteOrNull) {
		return checklists.hazard(hazardId, severity, status, statusNoteOrNull);
	}

	/** A hazard of this checklist that links an earlier one, in the stated severity and status. */
	private ObjectNode link(String hazardId, String checklistId, String earlierHazardId, String severity,
			String status) {
		ObjectNode hazard = checklists.hazard(hazardId, severity, status, "OPEN".equals(status) ? null
				: "Seen at this visit");
		hazard.put("description", "The railing seen again");
		ObjectNode previous = checklists.object();
		previous.put("checklistId", checklistId);
		previous.put("hazardId", earlierHazardId);
		hazard.set("previous", previous);
		return hazard;
	}

	private void changeIntent(String leadId, String intent, Map<String, Object> priceOrNull) throws Exception {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("expectedVersion", survey.version(leadId));
		change.put("newIntent", intent);
		change.put("askingPrice", priceOrNull);
		change.put("reason", "Owner changed plans");
		change.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		SurveyApiTestSupport.ok(api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes",
				change));
	}

	/** All warnings as "TYPE/hazardId". */
	private static List<String> warnings(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		checklist.path("readiness").path("warnings").forEach(item -> items.add(item.path("type").asString() + "/"
				+ item.path("hazardId").asString()));
		return items;
	}

	/** The EARLIER_HAZARD_OPEN warnings only. */
	private static List<String> earlier(JsonNode checklist) {
		return warnings(checklist).stream().filter(item -> item.startsWith("EARLIER_HAZARD_OPEN/")).toList();
	}

	/** {@code earlierHazards[].addressedBy} of the earlier hazard as "checklistId/hazardId/KIND". */
	private static List<String> addressedBy(JsonNode checklist, String earlierHazardId) {
		for (JsonNode earlier : checklist.path("earlierHazards")) {
			if (earlierHazardId.equals(earlier.path("hazard").path("hazardId").asString())) {
				List<String> items = new ArrayList<>();
				earlier.path("addressedBy").forEach(by -> items.add(by.path("checklistId").asString() + "/"
						+ by.path("hazardId").asString() + "/" + by.path("kind").asString()));
				return items;
			}
		}
		throw new AssertionError("no earlier hazard " + earlierHazardId + " in " + checklist.path("earlierHazards"));
	}
}
