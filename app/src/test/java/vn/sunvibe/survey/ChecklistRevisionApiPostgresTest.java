package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;

/**
 * POST /survey-checklists/{id}/revisions and the W16 start next to it (survey-checklist submission spec 4.6, A-2, A-7,
 * S-11, S-12): a new revision only after a return for documents, what it copies and carries, the intake rule, and the
 * link from a later visit. Needs Docker.
 */
class ChecklistRevisionApiPostgresTest extends MediaApiTestBase {

	private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder()
			.build();

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
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0966");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Revision Sale", "SALE");
		otherSale = survey.user("Revision Other Sale", "SALE");
		leader = survey.user("Revision Leader", "SALE_LEADER");
		admin = survey.user("Revision Admin", "ADMIN");
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

	private Ready returned(String kind) throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), kind);
		return ready;
	}

	private Result revise(TestUser caller, String checklistId, Map<String, Object> intakeOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", intakeOrNull);
		return checklists.api().post(caller, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId + "/revisions",
				body);
	}

	private String revised(TestUser caller, String checklistId, Map<String, Object> intakeOrNull) throws Exception {
		Result answer = revise(caller, checklistId, intakeOrNull);
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("checklist").path("id").asString();
	}

	private static Map<String, Object> intake() {
		return ChecklistApiTestSupport.intake("Took the lead over", LocalDate.now(), "Handover note of the leader");
	}

	private void assign(String leadId, TestUser to) throws Exception {
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", to.userId(), "expectedVersion", survey.version(leadId))));
	}

	private static void assertRefused(Result refused, int status, String code, String fieldOrNull) {
		assertEquals(status, refused.status(), refused.rawBody());
		assertEquals(code, refused.body().path("code").asString(), refused.rawBody());
		if (fieldOrNull != null) {
			assertEquals(fieldOrNull, refused.body().path("errors").path(0).path("field").asString(), refused.rawBody());
		}
	}

	private List<String> selected(String checklistId) {
		return jdbc.queryForList("select media_id from survey_checklist_media_selections where checklist_id = ?"
				+ " and deselected_at is null order by media_id", String.class, checklistId);
	}

	// ==================== when (spec 4.6) ====================

	@Test
	void revision_onlyAfterDocumentsOnly() throws Exception {
		Ready draft = review.ready(leader, sale);
		assertRefused(revise(sale, draft.checklistId(), null), 409, "CHECKLIST_NOT_ALLOWED", "status");

		review.submitted(sale, draft.checklistId());
		assertRefused(revise(sale, draft.checklistId(), null), 409, "CHECKLIST_NOT_ALLOWED", "status");

		Ready resurvey = returned("RESURVEY_REQUIRED");
		assertRefused(revise(sale, resurvey.checklistId(), null), 409, "CHECKLIST_NOT_ALLOWED", "status");
	}

	@Test
	void revision_secondStart_alreadyStarted() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");
		revised(sale, ready.checklistId(), null);

		assertRefused(revise(sale, ready.checklistId(), null), 409, "CHECKLIST_ALREADY_STARTED", null);
	}

	@Test
	void revision_heldLead_refused() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");
		Map<String, Object> hold = new LinkedHashMap<>();
		hold.put("toStatus", "ON_HOLD");
		hold.put("expectedVersion", survey.version(ready.leadId()));
		hold.put("note", "Owner abroad");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/status-changes", hold));

		assertRefused(revise(sale, ready.checklistId(), null), 409, "CHECKLIST_NOT_ALLOWED", "status");
	}

	// ==================== what it copies and carries (spec 4.6, S-12) ====================

	@Test
	void revision_copiesContentAndProvenance() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");
		long leadVersion = survey.version(ready.leadId());

		Result answer = revise(sale, ready.checklistId(), null);

		assertEquals(201, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		String next = body.path("checklist").path("id").asString();
		assertEquals(2, body.path("checklist").path("checklistNo").asInt());
		assertEquals("DRAFT", body.path("checklist").path("status").asString());
		assertEquals("CHECKLIST_REVISION_STARTED", body.path("historyEntry").path("type").asString());
		assertEquals(leadVersion, survey.version(ready.leadId()), "a revision never writes the lead row");
		assertEquals(ready.checklistId(), jdbc.queryForObject("select previous_checklist_id from survey_checklists"
				+ " where id = ?", String.class, next));
		for (Map<String, Object> group : jdbc.queryForList("select group_key, content, version from"
				+ " survey_checklist_groups where checklist_id = ?", next)) {
			assertEquals(0L, ((Number) group.get("version")).longValue(), group.get("group_key").toString());
			assertEquals(JSON.readTree(jdbc.queryForObject("select content from survey_checklist_groups where"
					+ " checklist_id = ? and group_key = ?", String.class, ready.checklistId(), group.get("group_key"))),
					JSON.readTree(group.get("content").toString()), "content and per-unit metadata unchanged");
		}
		assertEquals(sale.userId(), jdbc.queryForObject("select holder_user_id from survey_checklists where id = ?",
				String.class, next));
	}

	@Test
	void revision_carriesOnlyUsablePhotos() throws Exception {
		Ready ready = review.ready(leader, sale);
		String held = review.photo(sale, ready.checklistId(), null, null, "DETAIL");
		String lost = review.photo(sale, ready.checklistId(), null, null, "DETAIL");
		review.submitted(sale, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
		media.held(held, admin.userId(), "SECURITY_INCIDENT");
		jdbc.update("update survey_media set content_missing_at = now() where id = ?", lost);

		String next = revised(sale, ready.checklistId(), null);

		assertEquals(ready.photos().stream().sorted().toList(), selected(next));
		assertEquals(ready.checklistId(), jdbc.queryForObject("select distinct carried_from_checklist_id from"
				+ " survey_checklist_media_selections where checklist_id = ?", String.class, next));
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media_sets where checklist_id = ?",
				Integer.class, next), "a revision with carried photos only has its set row");
	}

	/** A photo carried twice keeps its origin. */
	@Test
	void revision_ofARevision_carriesWithTheOrigin() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");
		String second = revised(sale, ready.checklistId(), null);
		review.submitted(sale, second);
		review.needsChanges(leader, second, "DOCUMENTS_ONLY");

		String third = revised(sale, second, null);

		assertEquals(ready.photos().stream().sorted().toList(), selected(third));
		assertEquals(ready.checklistId(), jdbc.queryForObject("select distinct carried_from_checklist_id from"
				+ " survey_checklist_media_selections where checklist_id = ?", String.class, third));
	}

	// ==================== who (spec A-7) ====================

	@Test
	void revision_byOtherThanHolder_needsIntake() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");
		assign(ready.leadId(), otherSale);

		assertRefused(revise(otherSale, ready.checklistId(), null), 400, "VALIDATION_FAILED", "intake");

		String next = revised(otherSale, ready.checklistId(), intake());

		Map<String, Object> takeover = jdbc.queryForMap("select takeover_no, kind, from_user_id, to_user_id, snapshot"
				+ " from survey_checklist_takeovers where checklist_id = ?", next);
		assertEquals(1, takeover.get("takeover_no"));
		assertEquals("REVISION_BY_OTHER", takeover.get("kind"));
		assertEquals(sale.userId(), takeover.get("from_user_id"), "plan PD-9: the previous revision's holder");
		assertEquals(otherSale.userId(), takeover.get("to_user_id"));
		assertEquals(null, takeover.get("snapshot"));
	}

	/** spec A-7: W16's "the performer sends none" is for the first start only. */
	@Test
	void revision_byPerformerNotHolder_needsIntake() throws Exception {
		Ready ready = review.ready(leader, sale);
		assign(ready.leadId(), otherSale);
		assertEquals(201, checklists.takeOver(otherSale, ready.checklistId(), ready.visit()).status());
		review.submitted(otherSale, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
		assign(ready.leadId(), sale);

		assertRefused(revise(sale, ready.checklistId(), null), 400, "VALIDATION_FAILED", "intake");
		assertEquals(201, revise(sale, ready.checklistId(), intake()).status());
	}

	@Test
	void revision_holderSendsIntake_refused() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");

		assertRefused(revise(sale, ready.checklistId(), intake()), 400, "VALIDATION_FAILED", "intake");
	}

	/** S-12: a copied value keeps its provenance, so someone else's value needs a correction to change. */
	@Test
	void revision_copiedValueOfOther_needsCorrectionOnChange() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");
		assign(ready.leadId(), otherSale);
		String next = revised(otherSale, ready.checklistId(), intake());

		Result refused = checklists.save(otherSale, next, "parts", c -> ChecklistApiTestSupport.set(c, "accessRoute",
				checklists.value("Another way in")));

		assertRefused(refused, 400, "VALIDATION_FAILED", "correction");
	}

	@Test
	void revision_refused_writesSecurityEvent() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");

		assertEquals(404, revise(otherSale, ready.checklistId(), null).status());
		assertEquals(403, revise(leader, ready.checklistId(), null).status());

		assertEquals(List.of("NOT_ASSIGNEE", "NOT_VISIBLE"), jdbc.queryForList("select outcome from"
				+ " survey_security_events where lead_id = ? and action = 'REVISION' order by outcome", String.class,
				ready.leadId()));
	}

	@Test
	void revision_body_withoutTheKey_400() throws Exception {
		Ready ready = returned("DOCUMENTS_ONLY");

		assertEquals(400, checklists.api().post(sale, ChecklistReviewApiSupport.CHECKLISTS + "/" + ready.checklistId()
				+ "/revisions", Map.of()).status());
	}

	// ==================== the W16 start (spec 4.6, A-2, S-11) ====================

	@Test
	void start_visitWithRevision_refused() throws Exception {
		Ready ready = returned("RESURVEY_REQUIRED");
		jdbc.update("update owner_leads set status = 'SURVEY_SCHEDULED' where id = ?", ready.leadId());

		assertRefused(checklists.start(sale, ready.visit().appointmentId(), null), 409, "CHECKLIST_ALREADY_STARTED",
				null);
	}

	@Test
	void start_newVisitAfterResurvey_linksPrevious() throws Exception {
		Ready ready = returned("RESURVEY_REQUIRED");
		assertEquals("CONTACTED", survey.leadStatus(ready.leadId()));
		Visit second = visitAgain(ready);

		String next = checklists.started(sale, second.appointmentId());

		assertEquals(ready.checklistId(), jdbc.queryForObject("select previous_checklist_id from survey_checklists"
				+ " where id = ?", String.class, next));
	}

	@Test
	void start_firstVisit_noPrevious() throws Exception {
		Ready ready = review.ready(leader, sale);

		assertEquals(null, jdbc.queryForObject("select previous_checklist_id from survey_checklists where id = ?",
				String.class, ready.checklistId()));
	}

	private Visit visitAgain(Ready ready) throws Exception {
		Instant start = SurveyApiTestSupport.startIn(24);
		Map<String, Object> booking = new LinkedHashMap<>(survey.booking(ready.leadId(), start));
		booking.put("resurveyReason", "The reviewer asked for a new visit");
		Result booked = survey.book(sale, ready.leadId(), booking);
		assertEquals(201, booked.status(), booked.rawBody());
		String appointment = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		Duration back = Duration.ofDays(2).plusHours(1);
		survey.shiftBack(appointment, back);
		Instant arrivedAt = start.minus(back);
		Result visited = checklists.recordVisit(sale, ready.leadId(), appointment, arrivedAt);
		assertEquals(200, visited.status(), visited.rawBody());
		return new Visit(ready.leadId(), appointment, arrivedAt);
	}
}
