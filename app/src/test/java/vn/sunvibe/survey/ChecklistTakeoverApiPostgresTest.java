package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

import java.math.BigDecimal;

import java.time.LocalDate;
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
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Who may reach a checklist and how a draft changes hands (survey-checklist spec 4.4, 4.5, 7.1; W16-15 with the group
 * saves; W16-38 and A-11 for the takeover basis). Needs Docker.
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
class ChecklistTakeoverApiPostgresTest {

	private static final List<String> TAKEOVER_FIELDS = List.of("takeoverNo", "kind", "fromUserId", "fromName",
			"toUserId", "toName", "reason", "basis", "occurredAt", "hasSnapshot");

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
	private TestUser saleA;
	private TestUser saleB;
	private TestUser otherSale;
	private TestUser leader;
	private final List<TestUser> managers = new ArrayList<>();
	private final List<TestUser> outsiders = new ArrayList<>();

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0962");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		saleA = survey.user("Takeover Sale A", "SALE");
		saleB = survey.user("Takeover Sale B", "SALE");
		otherSale = survey.user("Takeover Other Sale", "SALE");
		leader = survey.user("Takeover Leader", "SALE_LEADER");
		for (String role : List.of("SALES_MANAGER", "ADMIN", "SUPER_ADMIN")) {
			managers.add(survey.user("Takeover " + role, role));
		}
		for (String role : List.of("LISTING_REVIEWER", "CUSTOMER", "OWNER")) {
			outsiders.add(survey.user("Takeover Outsider " + role, role));
		}
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W16-15: roles ====================

	@Test
	void rolesOutsideTheStaff_areForbidden_andNoTokenIsUnauthorized() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());

		for (TestUser outsider : outsiders) {
			assertEquals(403, checklists.start(outsider, visit.appointmentId(), null).status(), outsider.username());
			assertEquals(403, checklists.read(outsider, id).status(), outsider.username());
			assertEquals(403, checklists.takeOver(outsider, id, visit).status(), outsider.username());
			assertEquals(403, checklists.checkpoint(outsider, id, 1).status(), outsider.username());
		}
		assertEquals(401, checklists.start(null, visit.appointmentId(), null).status());
		assertEquals(401, checklists.read(null, id).status());
		assertEquals(401, checklists.takeOver(null, id, visit).status());
		assertEquals(401, checklists.checkpoint(null, id, 1).status());
		assertEquals(List.of(), checklists.securityEvents(visit.leadId()));
	}

	@Test
	void managersRead_withNotAssignee_butCannotStartOrTakeOver() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		Visit notStarted = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());

		for (TestUser manager : managers) {
			Result read = checklists.readOk(manager, id);
			assertFalse(read.body().path("editable").asBoolean(), manager.username());
			assertEquals("NOT_ASSIGNEE", read.body().path("readOnlyReason").asString(), manager.username());
			assertEquals(0, read.body().path("leadStaff").size(), manager.username());
			assertEquals(403, checklists.start(manager, notStarted.appointmentId(), null).status(),
					manager.username());
			assertEquals(403, checklists.takeOver(manager, id, visit).status(), manager.username());
		}
		assertEquals(saleA.userId(), checklists.readOk(saleA, id).body().path("holderUserId").asString());
		assertEquals(0, checklists.rows("select count(*) from survey_checklist_takeovers where checklist_id = ?", id));
	}

	@Test
	void anotherSale_getsNotFoundOnEveryEndpoint() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		Visit notStarted = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());

		assertError(checklists.start(otherSale, notStarted.appointmentId(), null), 404, "NOT_FOUND", null);
		assertError(checklists.read(otherSale, id), 404, "NOT_FOUND", null);
		assertError(checklists.takeOver(otherSale, id, visit), 404, "NOT_FOUND", null);
		assertError(checklists.checkpoint(otherSale, id, 1), 404, "NOT_FOUND", null);
	}

	// ==================== W16-15: takeover ====================

	@Test
	void afterReassignment_theNewAssigneeTakesOver_withACheckpoint_andTheOldOneLosesAccessAtOnce()
			throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		JsonNode before = checklists.readOk(saleA, id).body();
		long versionBefore = checklists.checklistVersion(id);

		checklists.reassign(leader, visit.leadId(), saleB);

		assertError(checklists.read(saleA, id), 404, "NOT_FOUND", null);
		JsonNode asB = checklists.readOk(saleB, id).body();
		assertFalse(asB.path("editable").asBoolean());
		assertEquals("TAKEOVER_REQUIRED", asB.path("readOnlyReason").asString());

		Result takenOver = checklists.takeOver(saleB, id, visit);

		assertEquals(201, takenOver.status(), takenOver.rawBody());
		assertEquals(List.of("checklist", "historyEntry"), AcquisitionApiTestSupport.fieldNames(takenOver.body()));
		JsonNode checklist = takenOver.body().path("checklist");
		assertEquals(saleB.userId(), checklist.path("holderUserId").asString());
		assertEquals("Takeover Sale B", checklist.path("holderName").asString());
		assertTrue(checklist.path("editable").asBoolean());
		assertEquals(versionBefore + 1, checklist.path("version").asLong());
		JsonNode takeover = checklist.path("takeovers").path(0);
		assertEquals(TAKEOVER_FIELDS, AcquisitionApiTestSupport.fieldNames(takeover));
		assertEquals(1, takeover.path("takeoverNo").asInt());
		assertEquals("TAKEOVER", takeover.path("kind").asString());
		assertEquals(saleA.userId(), takeover.path("fromUserId").asString());
		assertEquals("Takeover Sale A", takeover.path("fromName").asString());
		assertEquals(saleB.userId(), takeover.path("toUserId").asString());
		assertTrue(takeover.path("hasSnapshot").asBoolean());
		JsonNode entry = takenOver.body().path("historyEntry");
		assertEquals("CHECKLIST_TAKEN_OVER", entry.path("type").asString());
		assertEquals(id, entry.path("referenceId").asString());
		List<Map<String, Object>> rows = checklists.activities(visit.leadId(), "CHECKLIST_TAKEN_OVER");
		assertEquals(1, rows.size());
		assertNull(rows.get(0).get("note"));

		Result checkpoint = checklists.checkpoint(saleB, id, 1);
		assertEquals(200, checkpoint.status(), checkpoint.rawBody());
		assertEquals(List.of("takeover", "snapshot"), AcquisitionApiTestSupport.fieldNames(checkpoint.body()));
		assertEquals(before.path("groups"), checkpoint.body().path("snapshot").path("groups"));
		assertError(checklists.checkpoint(saleA, id, 1), 404, "NOT_FOUND", null);
		assertError(checklists.checkpoint(saleB, id, 2), 404, "NOT_FOUND", null);

		checklists.reassign(leader, visit.leadId(), saleA);
		Result back = checklists.takeOver(saleA, id, visit);

		assertEquals(201, back.status(), back.rawBody());
		assertEquals(2, back.body().path("checklist").path("takeovers").size());
		assertEquals(200, checklists.checkpoint(saleA, id, 2).status());
		assertEquals(2, checklists.activities(visit.leadId(), "CHECKLIST_TAKEN_OVER").size());
	}

	@Test
	void takeover_byTheHolder_isNotAllowedOnHolder() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());

		Result result = checklists.takeOver(saleA, id, visit);

		assertError(result, 409, "CHECKLIST_NOT_ALLOWED", "holder");
	}

	@Test
	void takeover_byAnIneligibleAssignee_isSurveyorIneligible() throws Exception {
		TestUser leaving = survey.user("Takeover Leaving", "SALE");
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		checklists.reassign(leader, visit.leadId(), leaving);
		checklists.makeIneligible(leaving);

		assertError(checklists.takeOver(leaving, id, visit), 422, "SURVEYOR_INELIGIBLE", null);
	}

	@Test
	void takeover_withAStaleVersion_isAConflictOnExpectedVersion() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		checklists.reassign(leader, visit.leadId(), saleB);

		Result stale = checklists.takeOver(saleB, id, checklists.checklistVersion(id) + 1, "New assignee",
				visit.arrivalDate(), "Leader handed it over");

		assertError(stale, 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		assertEquals("This checklist changed since you opened it.", stale.body().path("message").asString());
	}

	@Test
	void groupSaves_byRole_andOnlyByTheHolderAfterTheTakeover() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		String spaceId = checklists.newId();
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		checklists.saved(saleA, id, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));
		JsonNode read = checklists.readOk(saleA, id).body();

		for (String group : ChecklistApiTestSupport.GROUPS) {
			ObjectNode content = ChecklistApiTestSupport.input(read, group);
			long version = ChecklistApiTestSupport.version(read, group);
			assertEquals(401, checklists.put(null, id, group, version, content, null, null).status(), group);
			for (TestUser outsider : outsiders) {
				assertEquals(403, checklists.put(outsider, id, group, version, content, null, null).status(), group);
			}
			assertError(checklists.put(otherSale, id, group, version, content, null, null), 404, "NOT_FOUND", null);
			for (TestUser manager : managers) {
				assertEquals(403, checklists.put(manager, id, group, version, content, null, null).status(), group);
			}
		}
		checklists.reassign(leader, visit.leadId(), saleB);

		assertError(checklists.save(saleB, id, "parts", c -> set(c, "accessRoute", checklists.value("Alley"))), 409,
				"CHECKLIST_TAKEOVER_REQUIRED", null);
		assertEquals(201, checklists.takeOver(saleB, id, visit).status());
		checklists.saved(saleB, id, "parts", c -> set(c, "accessRoute", checklists.value("Alley")));
		Result checkpoint = checklists.checkpoint(saleB, id, 1);
		assertEquals(read.path("groups"), checkpoint.body().path("snapshot").path("groups"));
		assertEquals(0, new BigDecimal("30").compareTo(checkpoint.body().path("snapshot").path("groups")
				.path("dimensions").path("content").path("spaces").path(0).path("area").path("value")
				.decimalValue()));
	}

	// ==================== W16-38, A-11 ====================

	@Test
	void takeover_basisDateAndTexts_areChecked() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		checklists.reassign(leader, visit.leadId(), saleB);
		long version = checklists.checklistVersion(id);
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);

		assertError(checklists.takeOver(saleB, id, version, "New assignee", visit.arrivalDate().minusDays(1),
				"Leader handed it over"), 400, "VALIDATION_FAILED", "basis.confirmedOn");
		assertError(checklists.takeOver(saleB, id, version, "New assignee", today.plusDays(1),
				"Leader handed it over"), 400, "VALIDATION_FAILED", "basis.confirmedOn");
		assertError(checklists.takeOver(saleB, id, version, "call 0905 123 456", visit.arrivalDate(),
				"Leader handed it over"), 400, "VALIDATION_FAILED", "reason");
		assertError(checklists.takeOver(saleB, id, version, "New assignee", visit.arrivalDate(), "0905 123 456"),
				400, "VALIDATION_FAILED", "basis.text");
		assertEquals(0, checklists.rows("select count(*) from survey_checklist_takeovers where checklist_id = ?", id));
	}
}
