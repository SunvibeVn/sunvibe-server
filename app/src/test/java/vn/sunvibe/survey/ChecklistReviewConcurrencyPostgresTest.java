package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyPort;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.integration.AcquisitionSurveyLeadAdapter;
import vn.sunvibe.integration.SurveyLeadEventsAdapter;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Submission, review and holds against each other on PostgreSQL (survey-checklist submission spec 5.4, S-10). A forced
 * order pauses one command at a point where it holds its locks and starts the other, which is proven to wait on a lock
 * in {@code pg_stat_activity} before the first is released; races without a pause start both at one gate, several rounds
 * with fresh leads. Never a 500, a deadlock or a state the rules refuse. Needs Docker.
 */
@Import(ChecklistReviewConcurrencyPostgresTest.PausingDoubles.class)
// The doubles make a context (and a PostgreSQL container) no other class reuses: close it after the class so the cached
// contexts of the suite fit in the test JVM
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistReviewConcurrencyPostgresTest extends MediaApiTestBase {

	private static final int ROUNDS = 3;
	/** The lead share lock of a submit, a save, a deselection or a hold. */
	static final String SHARE = "findVisibleForShare";
	/** The History insert, the last call of a checklist command: every lock and write of it is held. */
	static final String ACTIVITY = "recordChecklistActivity";
	/** The lead move of a decision or a booking: the lead row and every other lock of it are held. */
	static final String LEAD_APPLY = "apply";
	/** A lead command's survey hook, after the lead row was updated. */
	static final String HOOK = "hook";
	/** A read of the checklist answer, built after the writes. */
	static final String OWNER_FORM = "ownerForm";

	static final AtomicReference<Pause> ARMED = new AtomicReference<>();

	static final class Pause {
		final String point;
		final String leadId;
		final CountDownLatch reached = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);

		Pause(String point, String leadId) {
			this.point = point;
			this.leadId = leadId;
		}
	}

	static void pauseAt(String point, Object leadId) {
		Pause pause = ARMED.get();
		if (pause == null || !pause.point.equals(point) || !pause.leadId.equals(leadId)
				|| !ARMED.compareAndSet(pause, null)) {
			return;
		}
		pause.reached.countDown();
		try {
			if (!pause.release.await(60, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Test pause was never released.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	@TestConfiguration
	static class PausingDoubles {

		@Bean
		@Primary
		SurveyLeadPort pausingSurveyLeadPort(AcquisitionSurveyLeadAdapter real) {
			return (SurveyLeadPort) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { SurveyLeadPort.class }, (proxy, method, args) -> {
						Object answer = invoke(real, method, args);
						if (args != null && Set.of(SHARE, ACTIVITY, LEAD_APPLY, OWNER_FORM).contains(method.getName())) {
							pauseAt(method.getName(), args[0]);
						}
						return answer;
					});
		}

		@Bean
		@Primary
		LeadSurveyPort pausingLeadSurveyHooks(SurveyLeadEventsAdapter real) {
			return (LeadSurveyPort) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { LeadSurveyPort.class }, (proxy, method, args) -> {
						Object answer = invoke(real, method, args);
						if (args != null && args.length > 0) {
							pauseAt(HOOK, args[0]);
						}
						return answer;
					});
		}

		private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
			try {
				return method.invoke(target, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
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
		survey = new SurveyApiTestSupport(api, jdbc, "0961");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		executor = Executors.newFixedThreadPool(6);
		sale = survey.user("Race Sale", "SALE");
		leader = survey.user("Race Leader", "SALE_LEADER");
		manager = survey.user("Race Manager", "SALES_MANAGER");
		admin = survey.user("Race Admin", "ADMIN");
	}

	@BeforeEach
	void open() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void disarm() {
		ARMED.set(null);
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		executor.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== decisions ====================

	@Test
	void decide_twoParallel_oneWins409() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Ready ready = submitted();
			List<TestUser> reviewers = List.of(leader, manager);

			List<Result> results = race(index -> review.decide(reviewers.get(index), ready.checklistId(), "PASSED"));

			assertOneWins(results, 200);
			assertEquals(1, count("select count(*) from survey_checklist_decisions where checklist_id = ?",
					ready.checklistId()));
			assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
		}
	}

	/** The decision holds the lead, so the hold comes after it and the PASSED stops being usable. */
	@Test
	void decide_vsAdminHold_decisionFirst_holdWaits() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitReached(pause, "decision");
		Future<Result> hold = async(() -> hold(ready.photos().get(0)));
		awaitLockWaiter("hold");
		pause.release.countDown();

		assertEquals(200, decision.get(60, TimeUnit.SECONDS).status());
		assertEquals(200, hold.get(60, TimeUnit.SECONDS).status());
		JsonNode passed = checklists.readOk(sale, ready.checklistId()).body().path("passed");
		assertFalse(passed.path("usable").asBoolean());
		assertEquals("EVIDENCE_HELD", passed.path("reason").asString());
	}

	@Test
	void decide_vsAdminHold_holdFirst_noPassedWithHeldPhoto() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(SHARE, ready.leadId());
		Future<Result> hold = async(() -> hold(ready.photos().get(0)));
		awaitReached(pause, "hold");
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitLockWaiter("decision");
		pause.release.countDown();

		assertEquals(200, hold.get(60, TimeUnit.SECONDS).status());
		Result refused = decision.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_DECISION_NOT_ALLOWED", refused.body().path("code").asString());
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(ready.leadId()));
	}

	@Test
	void intentChange_vsDecidePassed_decisionFirst() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitReached(pause, "decision");
		Future<Result> change = async(() -> changeIntent(ready.leadId()));
		awaitLockWaiter("requirement change");
		pause.release.countDown();

		assertEquals(200, decision.get(60, TimeUnit.SECONDS).status());
		assertEquals(409, change.get(60, TimeUnit.SECONDS).status());
		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
		assertEquals(1, count("select intent_cycle from owner_leads where id = ?", ready.leadId()));
	}

	@Test
	void intentChange_vsDecidePassed_changeFirst() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(HOOK, ready.leadId());
		Future<Result> change = async(() -> changeIntent(ready.leadId()));
		awaitReached(pause, "requirement change");
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitLockWaiter("decision");
		pause.release.countDown();

		assertEquals(200, change.get(60, TimeUnit.SECONDS).status());
		Result refused = decision.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("SUBMITTED", text("select status from survey_checklists where id = ?", ready.checklistId()));
	}

	@Test
	void decide_vsReassign_serialised() throws Exception {
		Ready first = submitted();
		Pause pause = arm(LEAD_APPLY, first.leadId());
		Future<Result> decision = async(() -> review.decide(manager, first.checklistId(), "PASSED"));
		awaitReached(pause, "decision");
		Future<Result> reassign = async(() -> reassign(first.leadId(), leader));
		awaitLockWaiter("reassignment");
		pause.release.countDown();
		assertEquals(200, decision.get(60, TimeUnit.SECONDS).status());
		assertTrue(reassign.get(60, TimeUnit.SECONDS).status() < 500);

		Ready second = submitted();
		pause = arm(HOOK, second.leadId());
		Future<Result> firstReassign = async(() -> reassign(second.leadId(), leader));
		awaitReached(pause, "reassignment");
		Future<Result> refused = async(() -> review.decide(leader, second.checklistId(), "PASSED"));
		awaitLockWaiter("decision");
		pause.release.countDown();
		assertEquals(200, firstReassign.get(60, TimeUnit.SECONDS).status());
		Result answer = refused.get(60, TimeUnit.SECONDS);
		assertEquals(403, answer.status(), answer.rawBody());
		assertEquals("CHECKLIST_REVIEW_NOT_ALLOWED", answer.body().path("code").asString());
	}

	@Test
	void decide_vsHold_serialised() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(HOOK, ready.leadId());
		Future<Result> held = async(() -> toStatus(ready.leadId(), "ON_HOLD", null));
		awaitReached(pause, "hold of the lead");
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitLockWaiter("decision");
		pause.release.countDown();

		assertEquals(200, held.get(60, TimeUnit.SECONDS).status());
		Result refused = decision.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("lead.status", refused.body().path("errors").path(0).path("field").asString());
	}

	@Test
	void decide_vsClose_decisionFirst_closeComesAfter() throws Exception {
		Ready ready = submitted();
		long version = survey.version(ready.leadId());
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitReached(pause, "decision");
		Future<Result> close = async(() -> toStatus(ready.leadId(), "WITHDRAWN", version));
		awaitLockWaiter("close");
		pause.release.countDown();

		assertEquals(200, decision.get(60, TimeUnit.SECONDS).status());
		assertEquals(409, close.get(60, TimeUnit.SECONDS).status(), "its version is stale");
		assertEquals("QUALIFIED", survey.leadStatus(ready.leadId()));
	}

	@Test
	void decide_vsClose_closeFirst_decisionRefused() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(HOOK, ready.leadId());
		Future<Result> close = async(() -> toStatus(ready.leadId(), "WITHDRAWN", null));
		awaitReached(pause, "close");
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitLockWaiter("decision");
		pause.release.countDown();

		assertEquals(200, close.get(60, TimeUnit.SECONDS).status());
		Result refused = decision.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("lead.status", refused.body().path("errors").path(0).path("field").asString());
		assertEquals("SUBMITTED", text("select status from survey_checklists where id = ?", ready.checklistId()));
	}

	/** Spec 9.5: the decision's answer is built after its commit, so the lead row is free while it is built. */
	@Test
	void decide_answerBuiltAfterTheCommit_theLeadIsFree() throws Exception {
		Ready ready = submitted();
		Pause pause = arm(OWNER_FORM, ready.leadId());
		Future<Result> decision = async(() -> review.decide(manager, ready.checklistId(), "PASSED"));
		awaitReached(pause, "answer of the decision");

		Result held = async(() -> toStatus(ready.leadId(), "ON_HOLD", null)).get(20, TimeUnit.SECONDS);
		pause.release.countDown();

		assertEquals(200, held.status(), held.rawBody());
		assertEquals(200, decision.get(60, TimeUnit.SECONDS).status());
	}

	// ==================== submit against booking (D3-7) ====================

	@Test
	void submit_vsBooking_submitFirst_bookingRefused() throws Exception {
		Ready ready = review.ready(leader, sale);
		Pause pause = arm(ACTIVITY, ready.leadId());
		Future<Result> submit = async(() -> review.submit(sale, ready.checklistId()));
		awaitReached(pause, "submit");
		Future<Result> booking = async(() -> rebook(ready.leadId()));
		awaitLockWaiter("booking");
		pause.release.countDown();

		assertEquals(200, submit.get(60, TimeUnit.SECONDS).status());
		Result refused = booking.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals(0, count("select count(*) from survey_appointments where lead_id = ? and status = 'SCHEDULED'",
				ready.leadId()));
	}

	@Test
	void submit_vsBooking_bookingFirst_submitRefused() throws Exception {
		Ready ready = review.ready(leader, sale);
		Pause pause = arm(LEAD_APPLY, ready.leadId());
		Future<Result> booking = async(() -> rebook(ready.leadId()));
		awaitReached(pause, "booking");
		Future<Result> submit = async(() -> review.submit(sale, ready.checklistId()));
		awaitLockWaiter("submit");
		pause.release.countDown();

		assertEquals(201, booking.get(60, TimeUnit.SECONDS).status());
		Result refused = submit.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_NOT_READY", refused.body().path("code").asString());
		assertEquals("appointment", refused.body().path("errors").path(0).path("field").asString());
		assertEquals("DRAFT", text("select status from survey_checklists where id = ?", ready.checklistId()));
	}

	// ==================== submit against a running save (spec 5.4) ====================

	@Test
	void submit_vsRunningGroupSave_saveFirst_theFrozenVersionsIncludeTheSave() throws Exception {
		Ready ready = review.ready(leader, sale);
		long before = count("select version from survey_checklist_groups where checklist_id = ? and group_key ="
				+ " 'SAFETY'", ready.checklistId());
		Pause pause = arm(ACTIVITY, ready.leadId());
		Future<Result> save = async(() -> saveEgress(ready.checklistId(), "The back door"));
		awaitReached(pause, "save");
		Future<Result> submit = async(() -> review.submit(sale, ready.checklistId()));
		awaitLockWaiter("submit");
		pause.release.countDown();

		assertEquals(200, save.get(60, TimeUnit.SECONDS).status());
		Result submitted = submit.get(60, TimeUnit.SECONDS);
		assertEquals(200, submitted.status(), submitted.rawBody());
		assertEquals(before + 1, count("select version from survey_checklist_groups where checklist_id = ? and"
				+ " group_key = 'SAFETY'", ready.checklistId()));
		assertTrue(text("select group_versions::text from survey_checklist_submissions where checklist_id = ?",
				ready.checklistId()).contains(String.valueOf(before + 1)));
	}

	@Test
	void submit_vsRunningGroupSave_submitFirst_theSaveAnswersReadOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		String stored = text("select content from survey_checklist_groups where checklist_id = ? and group_key"
				+ " = 'SAFETY'", ready.checklistId());
		Pause pause = arm(ACTIVITY, ready.leadId());
		Future<Result> submit = async(() -> review.submit(sale, ready.checklistId()));
		awaitReached(pause, "submit");
		Future<Result> save = async(() -> saveEgress(ready.checklistId(), "The back door"));
		awaitLockWaiter("save");
		pause.release.countDown();

		assertEquals(200, submit.get(60, TimeUnit.SECONDS).status());
		Result refused = save.get(60, TimeUnit.SECONDS);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_READ_ONLY", refused.body().path("code").asString());
		assertEquals(stored, text("select content from survey_checklist_groups where checklist_id = ? and"
				+ " group_key = 'SAFETY'", ready.checklistId()));
	}

	@Test
	void submit_twoParallelBySameCaller_oneSubmission() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Ready ready = review.ready(leader, sale);
			long version = review.version(ready.checklistId());

			List<Result> results = race(index -> review.submit(sale, ready.checklistId(), version));

			assertEquals(List.of(200, 200), results.stream().map(Result::status).toList(), statuses(results));
			assertEquals(1, results.stream().filter(result -> result.body().path("historyEntry").isNull()).count(),
					"the loser replays");
			assertEquals(1, count("select count(*) from survey_checklist_submissions where checklist_id = ?",
					ready.checklistId()));
			assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type ="
					+ " 'CHECKLIST_SUBMITTED'", ready.leadId()));
		}
	}

	@Test
	void submit_vsDeselectAndUpload_frozenSetConsistent() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Ready ready = review.ready(leader, sale);
			review.submitted(sale, ready.checklistId());
			review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
			String second = revised(ready.checklistId());
			String extra = review.photo(sale, second, ready.spaceId(), "A1", "MAIN_SPACE");
			long version = review.version(second);
			String carried = jdbc.queryForObject("select media_id from survey_checklist_media_selections where"
					+ " checklist_id = ? and media_id in (select id from survey_media where category = 'MAIN_SPACE')",
					String.class, second);
			int seed = 7000 + round * 10;

			List<Result> results = race3(() -> review.submit(sale, second, version),
					() -> deselect(second, carried),
					() -> create(second, seed));

			results.forEach(result -> assertTrue(result.status() < 500, result.rawBody()));
			if (results.get(0).status() == 200) {
				Set<String> frozen = new HashSet<>(jdbc.queryForList("select media_id from survey_checklist_evidence"
						+ " where checklist_id = ?", String.class, second));
				Set<String> selected = new HashSet<>(jdbc.queryForList("select media_id from"
						+ " survey_checklist_media_selections where checklist_id = ? and deselected_at is null",
						String.class, second));
				selected.addAll(jdbc.queryForList("select id from survey_media where checklist_id = ? and removed_at"
						+ " is null and status = 'READY'", String.class, second));
				assertEquals(selected, frozen, "the frozen set is what was selected at the freeze");
				assertTrue(frozen.contains(extra));
			}
		}
	}

	// ==================== holds of carried photos (spec 5.4) ====================

	@Test
	void hold_carriedPhoto_vsCascadeInSelectingRevision_noDeadlock() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Ready ready = review.ready(leader, sale);
			review.submitted(sale, ready.checklistId());
			review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
			String second = revised(ready.checklistId());
			String photo = ready.photos().get(2);

			List<Result> results = race(index -> index == 0 ? hold(photo) : checklists.save(sale, second,
					"identification", c -> c.setAll(checklists.identification("WHOLE", checklists.space(
							checklists.newId(), "A2")))));

			assertEquals(200, results.get(0).status(), results.get(0).rawBody());
			assertEquals(200, results.get(1).status(), results.get(1).rawBody());
			assertEquals(1, count("select count(*) from survey_media where id = ? and held_at is not null", photo));
		}
	}

	// ==================== helpers ====================

	private Ready submitted() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		return ready;
	}

	private String revised(String checklistId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", null);
		Result answer = checklists.api().post(sale, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId
				+ "/revisions", body);
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("checklist").path("id").asString();
	}

	private Result deselect(String checklistId, String mediaId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("correction", null);
		return checklists.api().post(sale, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId + "/media/" + mediaId
				+ "/deselection", body);
	}

	private Result create(String checklistId, int seed) throws Exception {
		byte[] bytes = MediaApiTestSupport.bytes(1500, seed);
		return media.create(sale, checklistId, MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", null, (long) bytes.length, MediaApiTestSupport.sha(bytes)));
	}

	private Result hold(String mediaId) throws Exception {
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

	private Result rebook(String leadId) throws Exception {
		Instant start = SurveyApiTestSupport.startIn(24);
		Map<String, Object> booking = new LinkedHashMap<>(survey.booking(leadId, start));
		booking.put("resurveyReason", "The owner asked for a second look");
		return survey.book(sale, leadId, booking);
	}

	private Result saveEgress(String checklistId, String text) throws Exception {
		return checklists.save(sale, checklistId, "safety", c -> ChecklistApiTestSupport.set(c, "egress",
				checklists.value(text)));
	}

	private long count(String sql, String id) {
		return jdbc.queryForObject(sql, Long.class, id);
	}

	private String text(String sql, String id) {
		return jdbc.queryForObject(sql, String.class, id);
	}

	private static Pause arm(String point, String leadId) {
		Pause pause = new Pause(point, leadId);
		ARMED.set(pause);
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
		return raceAll(2, call);
	}

	@SafeVarargs
	private List<Result> race3(Callable<Result>... calls) throws Exception {
		return raceAll(calls.length, index -> {
			try {
				return calls[index].call();
			} catch (Exception e) {
				throw e;
			}
		});
	}

	private List<Result> raceAll(int size, Call call) throws Exception {
		CountDownLatch ready = new CountDownLatch(size);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<Result>> futures = new ArrayList<>();
		for (int i = 0; i < size; i++) {
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

	private static void assertOneWins(List<Result> results, int okStatus) {
		List<Integer> statuses = results.stream().map(Result::status).toList();
		assertEquals(1, statuses.stream().filter(status -> status == okStatus).count(), statuses(results));
		assertTrue(statuses.stream().allMatch(status -> status == okStatus || status == 409), statuses(results));
	}

	private static String statuses(List<Result> results) {
		return results.stream().map(result -> result.status() + " " + result.body().path("code").asString())
				.toList().toString();
	}
}
