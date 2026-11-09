package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;
import static vn.sunvibe.survey.SurveyApiTestSupport.ok;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.adapter.OwnerLeadActivityRepositoryAdapter;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.ChecklistReviewConcurrencyPostgresTest.Pause;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;

/**
 * The requalification against the lead's other commands on PostgreSQL (property-create requalification spec 5.3, S-5,
 * S-8, S-11). A forced order pauses one command at a point where it holds its locks and starts the other, which is
 * proven to wait on a lock in {@code pg_stat_activity} before the first is released. Whichever runs first, the result is
 * one of the two in full: no half state, no 500. Needs Docker.
 */
@Import({ ChecklistReviewConcurrencyPostgresTest.PausingDoubles.class,
		ChecklistRequalificationConcurrencyPostgresTest.PausingLeadActivities.class })
// The doubles make a context (and a database on the shared server) no other class reuses: close it after the class so
// the cached contexts of the suite fit in the test JVM
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistRequalificationConcurrencyPostgresTest extends MediaApiTestBase {

	private static final int ROUNDS = 3;
	/** The lead move of the mark: the lead row, the checklist row and the mark's other writes are held. */
	private static final String LEAD_APPLY = ChecklistReviewConcurrencyPostgresTest.LEAD_APPLY;
	/** The lead share lock of a photo hold. */
	private static final String SHARE = ChecklistReviewConcurrencyPostgresTest.SHARE;
	/** A lead command's survey hook, after the lead row was updated. */
	private static final String HOOK = ChecklistReviewConcurrencyPostgresTest.HOOK;
	/** A read of the answer, built after the commit. */
	private static final String OWNER_FORM = ChecklistReviewConcurrencyPostgresTest.OWNER_FORM;
	/** A lead command's History entry, after the lead row was updated: no hook runs on a QUALIFIED lead. */
	private static final String LEAD_ACTIVITY = "leadActivity";

	@TestConfiguration
	static class PausingLeadActivities {

		@Bean
		@Primary
		OwnerLeadActivityDomainRepository pausingLeadActivities(OwnerLeadActivityRepositoryAdapter real) {
			return (OwnerLeadActivityDomainRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { OwnerLeadActivityDomainRepository.class }, (proxy, method, args) -> {
						Object answer;
						try {
							answer = method.invoke(real, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
						if ("save".equals(method.getName())) {
							ChecklistReviewConcurrencyPostgresTest.pauseAt(LEAD_ACTIVITY,
									((OwnerLeadActivity) args[0]).getLeadId().getValue());
						}
						return answer;
					});
		}
	}

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
	private ExecutorService executor;
	private TestUser sale;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0998");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		executor = Executors.newFixedThreadPool(6);
		sale = survey.user("Requalify Race Sale", "SALE");
		leader = survey.user("Requalify Race Leader", "SALE_LEADER");
		manager = survey.user("Requalify Race Manager", "SALES_MANAGER");
		admin = survey.user("Requalify Race Admin", "ADMIN");
	}

	@BeforeEach
	void open() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void disarm() {
		ChecklistReviewConcurrencyPostgresTest.ARMED.set(null);
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		executor.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== two marks ====================

	/** Review focus 1: one mark and one History row; the other answers 409. */
	@Test
	void mark_twoParallel_oneWins() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Ready ready = passed();
			Map<String, Object> body = body(ready.checklistId());
			List<TestUser> managers = List.of(leader, manager);

			List<Result> results = race(index -> requalify(managers.get(index), ready.checklistId(), body));

			List<Integer> statuses = results.stream().map(Result::status).toList();
			assertEquals(1, statuses.stream().filter(status -> status == 200).count(), statuses.toString());
			assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 409), statuses.toString());
			assertMarkedOnce(ready, "CONTACTED");
		}
	}

	@Test
	void mark_twoParallel_secondWaits_thenIsAConcurrentChange() throws Exception {
		Ready ready = passed();
		Map<String, Object> body = body(ready.checklistId());
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> first = async(() -> requalify(manager, ready.checklistId(), body));
		awaitReached(pause, "first mark");
		Future<Result> second = async(() -> requalify(leader, ready.checklistId(), body));
		awaitLockWaiter("second mark");
		pause.release.countDown();

		ok(first.get(60, TimeUnit.SECONDS));
		assertError(second.get(60, TimeUnit.SECONDS), 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		assertMarkedOnce(ready, "CONTACTED");
	}

	// ==================== a hold of a frozen photo (review focus 2) ====================

	@Test
	void mark_vsHold_markFirst_holdWaits() throws Exception {
		Ready ready = passed();
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitReached(pause, "mark");
		Future<Result> hold = async(() -> holdPhoto(ready.photos().get(0)));
		awaitLockWaiter("photo hold");
		pause.release.countDown();

		ok(mark.get(60, TimeUnit.SECONDS));
		ok(hold.get(60, TimeUnit.SECONDS));
		assertMarkedOnce(ready, "CONTACTED");
		assertNotNull(media.row(ready.photos().get(0)).get("held_at"));
	}

	@Test
	void mark_vsHold_holdFirst_markWaits() throws Exception {
		Ready ready = passed();
		Pause pause = arm(SHARE, ready.leadId());
		Future<Result> hold = async(() -> holdPhoto(ready.photos().get(0)));
		awaitReached(pause, "photo hold");
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitLockWaiter("mark");
		pause.release.countDown();

		ok(hold.get(60, TimeUnit.SECONDS));
		ok(mark.get(60, TimeUnit.SECONDS));
		assertMarkedOnce(ready, "CONTACTED");
		assertNotNull(media.row(ready.photos().get(0)).get("held_at"));
		assertEquals("REQUALIFIED", checklists.readOk(leader, ready.checklistId()).body().path("passed").path("reason")
				.asString());
	}

	// ==================== a reassignment (review focus 2) ====================

	@Test
	void mark_vsReassign_markFirst_reassignRefused() throws Exception {
		Ready ready = passed();
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitReached(pause, "mark");
		Future<Result> reassign = async(() -> reassign(ready.leadId(), leader));
		awaitLockWaiter("reassignment");
		pause.release.countDown();

		ok(mark.get(60, TimeUnit.SECONDS));
		assertEquals(409, reassign.get(60, TimeUnit.SECONDS).status(), "its lead version is stale");
		assertMarkedOnce(ready, "CONTACTED");
		assertEquals(sale.userId(), assignee(ready.leadId()));
	}

	@Test
	void mark_vsReassign_reassignFirst_markFollows() throws Exception {
		Ready ready = passed();
		Pause pause = arm(HOOK, ready.leadId());
		Future<Result> reassign = async(() -> reassign(ready.leadId(), leader));
		awaitReached(pause, "reassignment");
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitLockWaiter("mark");
		pause.release.countDown();

		ok(reassign.get(60, TimeUnit.SECONDS));
		ok(mark.get(60, TimeUnit.SECONDS));
		assertMarkedOnce(ready, "CONTACTED");
		assertEquals(leader.userId(), assignee(ready.leadId()));
	}

	// ==================== a hold of the lead ====================

	@Test
	void mark_vsLeadHold_markFirst_holdRefused() throws Exception {
		Ready ready = passed();
		long version = survey.version(ready.leadId());
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitReached(pause, "mark");
		Future<Result> hold = async(() -> toStatus(ready.leadId(), "ON_HOLD", version));
		awaitLockWaiter("hold of the lead");
		pause.release.countDown();

		ok(mark.get(60, TimeUnit.SECONDS));
		assertEquals(409, hold.get(60, TimeUnit.SECONDS).status(), "its version is stale");
		assertMarkedOnce(ready, "CONTACTED");
	}

	/** The hold commits first, so the mark reads ON_HOLD under the lock and the lead stays held. */
	@Test
	void mark_vsLeadHold_holdFirst_leadStaysHeld() throws Exception {
		Ready ready = passed();
		Pause pause = arm(LEAD_ACTIVITY, ready.leadId());
		Future<Result> hold = async(() -> toStatus(ready.leadId(), "ON_HOLD", null));
		awaitReached(pause, "hold of the lead");
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitLockWaiter("mark");
		pause.release.countDown();

		ok(hold.get(60, TimeUnit.SECONDS));
		ok(mark.get(60, TimeUnit.SECONDS));
		assertMarkedOnce(ready, "ON_HOLD");
		assertEquals("ON_HOLD", text("select lead_status_before from survey_checklist_requalifications where checklist_id"
				+ " = ?", ready.checklistId()));
	}

	// ==================== a close ====================

	@Test
	void mark_vsClose_markFirst_closeRefused() throws Exception {
		Ready ready = passed();
		long version = survey.version(ready.leadId());
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitReached(pause, "mark");
		Future<Result> close = async(() -> toStatus(ready.leadId(), "WITHDRAWN", version));
		awaitLockWaiter("close");
		pause.release.countDown();

		ok(mark.get(60, TimeUnit.SECONDS));
		assertEquals(409, close.get(60, TimeUnit.SECONDS).status(), "its version is stale");
		assertMarkedOnce(ready, "CONTACTED");
	}

	@Test
	void mark_vsClose_closeFirst_markRefused() throws Exception {
		Ready ready = passed();
		Pause pause = arm(LEAD_ACTIVITY, ready.leadId());
		Future<Result> close = async(() -> toStatus(ready.leadId(), "WITHDRAWN", null));
		awaitReached(pause, "close");
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitLockWaiter("mark");
		pause.release.countDown();

		ok(close.get(60, TimeUnit.SECONDS));
		assertError(mark.get(60, TimeUnit.SECONDS), 409, "INVALID_STATE_TRANSITION", "status");
		assertEquals("WITHDRAWN", survey.leadStatus(ready.leadId()));
		assertNotMarked(ready);
	}

	// ==================== a requirement change, on a held lead: QUALIFIED refuses it outright ====================

	@Test
	void mark_vsRequirementChange_markFirst_changeRefused() throws Exception {
		Ready ready = held();
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitReached(pause, "mark");
		Future<Result> change = async(() -> changeIntent(ready.leadId()));
		awaitLockWaiter("requirement change");
		pause.release.countDown();

		ok(mark.get(60, TimeUnit.SECONDS));
		assertEquals(409, change.get(60, TimeUnit.SECONDS).status(), "its lead version is stale");
		assertMarkedOnce(ready, "ON_HOLD");
		assertEquals(1, count("select intent_cycle from owner_leads where id = ?", ready.leadId()));
	}

	/** The change reads the PASSED with the lead row written, so it is refused and rolls back; the mark follows. */
	@Test
	void mark_vsRequirementChange_changeFirst_changeRefused() throws Exception {
		Ready ready = held();
		Pause pause = arm(HOOK, ready.leadId());
		Future<Result> change = async(() -> changeIntent(ready.leadId()));
		awaitReached(pause, "requirement change");
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitLockWaiter("mark");
		pause.release.countDown();

		assertEquals(409, change.get(60, TimeUnit.SECONDS).status());
		ok(mark.get(60, TimeUnit.SECONDS));
		assertMarkedOnce(ready, "ON_HOLD");
		assertEquals(1, count("select intent_cycle from owner_leads where id = ?", ready.leadId()));
	}

	// ==================== the answer (spec 5.3, W18 9.5) ====================

	@Test
	void mark_answerBuiltAfterTheCommit_theLeadIsFree() throws Exception {
		Ready ready = passed();
		Pause pause = arm(OWNER_FORM, ready.leadId());
		Future<Result> mark = async(() -> requalify(manager, ready.checklistId()));
		awaitReached(pause, "answer of the mark");

		Result held = async(() -> toStatus(ready.leadId(), "ON_HOLD", null)).get(20, TimeUnit.SECONDS);
		pause.release.countDown();

		ok(held);
		ok(mark.get(60, TimeUnit.SECONDS));
	}

	// ==================== the flows (spec S-5, S-11) ====================

	/** Review focus 3: a new visit of the same cycle passes through an independent review; the old revision still reads. */
	@Test
	void flow_markRevisitPass_sameCycle() throws Exception {
		Ready first = passed();
		ok(requalify(manager, first.checklistId()));
		assertEquals("CONTACTED", survey.leadStatus(first.leadId()));

		String next = newRevision(first.leadId());
		String space = checklists.newId();
		review.fill(sale, next, space);
		review.photos(sale, next, space);
		review.submitted(sale, next);
		assertEquals(403, review.decide(sale, next, "PASSED").status(), "the assignee has no reviewer role");
		review.passed(manager, next);

		assertEquals("QUALIFIED", survey.leadStatus(first.leadId()));
		assertEquals(1, count("select intent_cycle from owner_leads where id = ?", first.leadId()));
		assertEquals(2, count("select count(*) from survey_checklists where lead_id = ? and status = 'PASSED'",
				first.leadId()));
		JsonNode old = checklists.readOk(leader, first.checklistId()).body();
		assertEquals("REQUALIFIED", old.path("passed").path("reason").asString());
		assertEquals("PASSED", old.path("decision").path("outcome").asString());
		JsonNode current = checklists.readOk(leader, next).body();
		assertEquals(first.checklistId(), current.path("previousChecklistId").asString(),
				"spec 4: the next reviewer reads the mark on the earlier revision");
		assertTrue(current.path("passed").path("usable").asBoolean(), current.path("passed").toString());
		assertTrue(current.path("requalification").isNull());
		JsonNode block = checklists.api().get(manager, SurveyApiTestSupport.LEADS + "/" + first.leadId() + "/survey")
				.body();
		assertTrue(block.path("survey").path("hasPassed").asBoolean());
		assertEquals(next, block.path("survey").path("requalification").path("checklistId").asString());
	}

	/** D5-19: the hazard resolved on the withdrawn revision warns again and blocks a PASSED until it is seen again. */
	@Test
	void flow_resolvedOnMarked_newVisitNeedsObservation() throws Exception {
		Ready first = review.ready(leader, sale);
		String h1 = checklists.newId();
		saveHazards(first.checklistId(), checklists.hazard(h1, "IMPORTANT", "OPEN", null));
		review.submitted(sale, first.checklistId());
		review.needsChanges(manager, first.checklistId(), "RESURVEY_REQUIRED");

		String second = newRevision(first.leadId());
		String secondSpace = checklists.newId();
		review.fill(sale, second, secondSpace);
		review.photos(sale, second, secondSpace);
		String h2 = checklists.newId();
		saveHazards(second, link(h2, first.checklistId(), h1, "OBSERVED_RESOLVED"));
		review.submitted(sale, second);
		review.passed(manager, second);
		ok(requalify(manager, second));
		assertEquals(List.of(second + "/" + h2 + "/RESOLVED"), addressedBy(checklists.readOk(leader, second).body(), h1));

		String third = newRevision(first.leadId());
		String thirdSpace = checklists.newId();
		review.fill(sale, third, thirdSpace);
		review.photos(sale, third, thirdSpace);
		assertTrue(warnings(checklists.readOk(sale, third).body()).contains("EARLIER_HAZARD_OPEN/" + h1));
		assertEquals(List.of(), addressedBy(checklists.readOk(sale, third).body(), h1));
		review.submitted(sale, third);

		Result refused = review.decide(manager, third, "PASSED");

		assertError(refused, 409, "CHECKLIST_DECISION_NOT_ALLOWED", "hazards");
		review.needsChanges(manager, third, "DOCUMENTS_ONLY");
		Map<String, Object> startBody = new LinkedHashMap<>();
		startBody.put("intake", null);
		Result started = checklists.api().post(sale, ChecklistApiTestSupport.CHECKLISTS + "/" + third + "/revisions",
				startBody);
		assertEquals(201, started.status(), started.rawBody());
		String fourth = started.body().path("checklist").path("id").asString();
		saveHazards(fourth, link(checklists.newId(), first.checklistId(), h1, "OBSERVED_RESOLVED"));
		assertFalse(warnings(checklists.readOk(sale, fourth).body()).contains("EARLIER_HAZARD_OPEN/" + h1));
		review.submitted(sale, fourth);
		review.passed(manager, fourth);
		assertEquals("QUALIFIED", survey.leadStatus(first.leadId()));
	}

	// ==================== helpers ====================

	private Ready passed() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		review.passed(leader, ready.checklistId());
		return ready;
	}

	private Ready held() throws Exception {
		Ready ready = passed();
		ok(toStatus(ready.leadId(), "ON_HOLD", null));
		return ready;
	}

	private String newRevision(String leadId) throws Exception {
		Visit visit = visitAgain(leadId);
		return checklists.started(sale, visit.appointmentId());
	}

	private Visit visitAgain(String leadId) throws Exception {
		Instant start = SurveyApiTestSupport.startIn(24);
		Map<String, Object> booking = new LinkedHashMap<>(survey.booking(leadId, start));
		booking.put("resurveyReason", "The requalification asks for a new visit");
		Result booked = survey.book(sale, leadId, booking);
		assertEquals(201, booked.status(), booked.rawBody());
		String appointmentId = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		Duration back = Duration.ofDays(2).plusHours(1);
		survey.shiftBack(appointmentId, back);
		Instant arrivedAt = start.minus(back);
		ok(checklists.recordVisit(sale, leadId, appointmentId, arrivedAt));
		return new Visit(leadId, appointmentId, arrivedAt);
	}

	private void saveHazards(String checklistId, ObjectNode... hazards) throws Exception {
		Result saved = checklists.save(sale, checklistId, "safety", c -> set(c, "hazards",
				checklists.value(List.of(hazards))));
		assertEquals(200, saved.status(), saved.rawBody());
	}

	/** A hazard of this checklist that links an earlier one. */
	private ObjectNode link(String hazardId, String checklistId, String earlierHazardId, String status) {
		ObjectNode hazard = checklists.hazard(hazardId, "IMPORTANT", status, "Seen at this visit");
		hazard.put("description", "The railing seen again");
		ObjectNode previous = checklists.object();
		previous.put("checklistId", checklistId);
		previous.put("hazardId", earlierHazardId);
		hazard.set("previous", previous);
		return hazard;
	}

	private static List<String> warnings(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		checklist.path("readiness").path("warnings").forEach(item -> items.add(item.path("type").asString() + "/"
				+ item.path("hazardId").asString()));
		return items;
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

	private Map<String, Object> body(String checklistId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", review.version(checklistId));
		body.put("reasonKind", "WRONG_ASSET");
		body.put("reason", "Sai tài sản");
		body.put("eventSource", "Chủ nhà báo");
		body.put("scope", "Toàn bộ bản khảo sát");
		body.put("basisDate", LocalDate.now(SurveyTimeRules.ZONE).toString());
		return body;
	}

	private Result requalify(TestUser caller, String checklistId) throws Exception {
		return requalify(caller, checklistId, body(checklistId));
	}

	private Result requalify(TestUser caller, String checklistId, Map<String, Object> body) throws Exception {
		return checklists.api().post(caller, ChecklistApiTestSupport.CHECKLISTS + "/" + checklistId + "/requalification",
				body);
	}

	private Result holdPhoto(String mediaId) throws Exception {
		return checklists.api().post(admin, "/api/v1/staff/survey-media/" + mediaId + "/hold",
				Map.of("reason", "SECURITY_INCIDENT"));
	}

	private Result reassign(String leadId, TestUser to) throws Exception {
		return checklists.api().post(manager, SurveyApiTestSupport.LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", to.userId(), "expectedVersion", survey.version(leadId)));
	}

	private Result toStatus(String leadId, String status, Long versionOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", status);
		body.put("expectedVersion", versionOrNull == null ? survey.version(leadId) : versionOrNull);
		if ("WITHDRAWN".equals(status)) {
			body.put("reason", "Owner sold the house");
		} else {
			body.put("note", "Owner abroad");
		}
		return checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/status-changes", body);
	}

	private Result changeIntent(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", survey.version(leadId));
		body.put("newIntent", "SHORT_TERM_RENT");
		body.put("askingPrice", null);
		body.put("reason", "Owner changed plans");
		body.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		return checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes", body);
	}

	private String assignee(String leadId) {
		return text("select assigned_user_id from owner_leads where id = ?", leadId);
	}

	/** The mark in full: the revision, its row, one History entry and the lead's status. */
	private void assertMarkedOnce(Ready ready, String leadStatus) {
		assertNotNull(jdbc.queryForObject("select requalified_at from survey_checklists where id = ?", Instant.class,
				ready.checklistId()));
		assertEquals(1, count("select count(*) from survey_checklist_requalifications where checklist_id = ?",
				ready.checklistId()));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type ="
				+ " 'REQUALIFICATION_MARKED'", ready.leadId()));
		assertEquals(leadStatus, survey.leadStatus(ready.leadId()));
	}

	private void assertNotMarked(Ready ready) {
		assertNull(jdbc.queryForObject("select requalified_at from survey_checklists where id = ?", Instant.class,
				ready.checklistId()));
		assertEquals(0, count("select count(*) from survey_checklist_requalifications where checklist_id = ?",
				ready.checklistId()));
		assertEquals(0, count("select count(*) from owner_lead_activities where lead_id = ? and type ="
				+ " 'REQUALIFICATION_MARKED'", ready.leadId()));
	}

	private long count(String sql, String id) {
		return jdbc.queryForObject(sql, Long.class, id);
	}

	private String text(String sql, String id) {
		return jdbc.queryForObject(sql, String.class, id);
	}

	private static Pause arm(String point, String leadId) {
		Pause pause = new Pause(point, leadId);
		ChecklistReviewConcurrencyPostgresTest.ARMED.set(pause);
		return pause;
	}

	private <T> Future<T> async(Callable<T> call) {
		return executor.submit(call);
	}

	private static void awaitReached(Pause pause, String context) throws InterruptedException {
		assertTrue(pause.reached.await(60, TimeUnit.SECONDS), context + ": the first command never reached its pause");
	}

	/** Proves that another backend waits on a lock before the paused command is released. */
	private void awaitLockWaiter(String context) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname ="
					+ " current_database()", Long.class) > 0) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(context + ": the second command never waited on a lock");
	}

	@FunctionalInterface
	private interface Call {
		Result send(int index) throws Exception;
	}

	private List<Result> race(Call call) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<Result>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			int index = i;
			futures.add(executor.submit(() -> {
				ready.countDown();
				gate.await();
				return call.send(index);
			}));
		}
		assertTrue(ready.await(30, TimeUnit.SECONDS), "workers did not start");
		gate.countDown();
		List<Result> results = new ArrayList<>();
		for (Future<Result> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}
		return results;
	}
}
