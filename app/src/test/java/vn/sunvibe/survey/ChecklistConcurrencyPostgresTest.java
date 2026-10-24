package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyPort;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.integration.AcquisitionSurveyLeadAdapter;
import vn.sunvibe.integration.SurveyLeadEventsAdapter;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Checklist commands against each other and against lead commands on PostgreSQL (survey-checklist spec 5.5; W16-4,
 * W16-17, W16-36, the plan's Review Focus 3). A forced order pauses one command at a point where it holds its locks
 * (a delegating double of the port it calls there) and starts the other, which is proven to wait on a lock in
 * {@code pg_stat_activity} before the first is released; races without a pause start both calls at one gate, five
 * rounds with fresh leads. Never a 500, a deadlock or a checklist write after the change that refuses it. Needs
 * Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, ChecklistConcurrencyPostgresTest.PausingDoubles.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// The doubles make a context (and a PostgreSQL container) no other class reuses: close it after the class so the
// cached contexts of the suite fit in the test JVM
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistConcurrencyPostgresTest {

	private static final int ROUNDS = 5;
	/** After the lead share lock of a checklist command, before any group lock. */
	static final String SHARE = "findVisibleForShare";
	/** The History insert, the last call of a checklist command: every lock and write of it is held. */
	static final String ACTIVITY = "recordChecklistActivity";
	/** A lead command's survey hook, after the lead row was updated. */
	static final String HOOK = "hook";
	/** The lead move of a W15 visit record, after the lead row was updated. */
	static final String LEAD_APPLY = "apply";

	/** The armed pause: the first call at its point for its lead signals and waits for the release. */
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
						if (args != null && Set.of(SHARE, ACTIVITY, LEAD_APPLY).contains(method.getName())) {
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

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private AcquisitionApiTestSupport api;
	private ExecutorService executor;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser saleC;
	private TestUser leader;

	private enum LeadCommand { REASSIGN, HOLD, REQUIREMENT, VISIT }

	private enum ChecklistCommand { SAVE, TAKEOVER, START }

	/** A lead set up for one race: the draft (null for a start), the re-survey to visit and the bodies to send. */
	private record Setup(Visit visit, String checklistId, String resurveyId, TestUser actor, ObjectNode parts,
			long partsVersion) {
	}

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0968");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		executor = Executors.newFixedThreadPool(4);
		saleA = survey.user("Checklist Race Sale A", "SALE");
		saleB = survey.user("Checklist Race Sale B", "SALE");
		saleC = survey.user("Checklist Race Sale C", "SALE");
		leader = survey.user("Checklist Race Leader", "SALE_LEADER");
	}

	@AfterEach
	void disarm() {
		Pause pause = ARMED.getAndSet(null);
		if (pause != null) {
			pause.release.countDown();
		}
	}

	@AfterAll
	void tearDown() {
		executor.shutdownNow();
		survey.cleanUp();
	}

	// ==================== W16-4 ====================

	@Test
	void concurrentStarts_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Visit visit = checklists.visited(leader, saleA);
			int history = historyRows(visit.leadId());

			List<Result> results = race(i -> checklists.start(saleA, visit.appointmentId(), null));

			String context = "round " + round + ": " + statuses(results);
			Result loser = results.get(1 - winnerOf(results, 201, context));
			assertError(loser, 409, "CHECKLIST_ALREADY_STARTED", null);
			String id = jdbc.queryForObject("select id from survey_checklists where lead_id = ?", String.class,
					visit.leadId());
			assertEquals(7, checklists.rows("select count(*) from survey_checklist_groups where checklist_id = ?", id),
					context);
			assertEquals(history + 1, historyRows(visit.leadId()), context);
		}
	}

	// ==================== W16-17 ====================

	@Test
	void checklistCommandFirst_theLeadCommandWaitsForTheShareLock_andBothSucceed() throws Exception {
		for (ChecklistCommand command : ChecklistCommand.values()) {
			for (LeadCommand leadCommand : LeadCommand.values()) {
				String context = command + " then " + leadCommand;
				Setup setup = setup(command, leadCommand);
				Pause pause = arm(SHARE, setup.visit().leadId());

				Future<Result> first = async(() -> send(command, setup));
				awaitReached(pause, context);
				Future<Result> second = async(() -> send(leadCommand, setup));
				awaitLockWaiter(context);
				pause.release.countDown();

				Result checklist = first.get(60, TimeUnit.SECONDS);
				Result lead = second.get(60, TimeUnit.SECONDS);
				assertEquals(command == ChecklistCommand.SAVE ? 200 : 201, checklist.status(),
						context + ": " + checklist.rawBody());
				assertEquals(200, lead.status(), context + ": " + lead.rawBody());
				assertCommitted(command, setup, context);
			}
		}
	}

	@Test
	void leadCommandFirst_theChecklistCommandWaits_andIsRefusedWithoutWriting() throws Exception {
		for (ChecklistCommand command : ChecklistCommand.values()) {
			for (LeadCommand leadCommand : LeadCommand.values()) {
				String context = leadCommand + " then " + command;
				Setup setup = setup(command, leadCommand);
				Pause pause = arm(leadCommand == LeadCommand.VISIT ? LEAD_APPLY : HOOK, setup.visit().leadId());

				Future<Result> first = async(() -> send(leadCommand, setup));
				awaitReached(pause, context);
				Future<Result> second = async(() -> send(command, setup));
				awaitLockWaiter(context);
				pause.release.countDown();

				Result lead = first.get(60, TimeUnit.SECONDS);
				Result checklist = second.get(60, TimeUnit.SECONDS);
				assertEquals(200, lead.status(), context + ": " + lead.rawBody());
				assertRefused(command, leadCommand, checklist, context);
				assertNotWritten(command, setup, context);
			}
		}
	}

	@Test
	void twoChecklistCommandsOnOneLead_runTogether_andNeverMoveTheLeadVersion() throws Exception {
		Setup setup = setup(ChecklistCommand.SAVE, LeadCommand.HOLD);
		String leadId = setup.visit().leadId();
		long leadVersion = survey.version(leadId);
		JsonNode read = checklists.readOk(saleA, setup.checklistId()).body();
		ObjectNode dimensions = ChecklistApiTestSupport.input(read, "dimensions");
		set(dimensions, "spaces.0.bedrooms", checklists.sourced(2, "OBSERVED_ON_SITE"));
		Pause pause = arm(ACTIVITY, leadId);

		Future<Result> paused = async(() -> send(ChecklistCommand.SAVE, setup));
		awaitReached(pause, "parts save");
		assertTrue(checklists.rows("select count(*) from pg_locks l join pg_class c on c.oid = l.relation where "
				+ "c.relname = 'owner_leads' and l.mode = 'RowShareLock' and l.granted and l.pid <> pg_backend_pid()")
				> 0, "the lead share lock is held while the save waits");
		Result other = checklists.put(saleA, setup.checklistId(), "dimensions", 0, dimensions, null, null);
		pause.release.countDown();

		assertEquals(200, other.status(), other.rawBody());
		assertEquals(200, paused.get(60, TimeUnit.SECONDS).status());
		assertEquals(leadVersion, survey.version(leadId));
	}

	// ==================== W16-36 ====================

	@Test
	void spaceRemoval_againstASaveOfThatSpace_inBothOrders() throws Exception {
		Race removalWaits = removalRace();
		Pause dimensionsFirst = arm(ACTIVITY, removalWaits.leadId());
		Future<Result> save = async(() -> checklists.put(saleA, removalWaits.id(), "dimensions",
				removalWaits.versionOf("dimensions"), removalWaits.dimensions(), null, null));
		awaitReached(dimensionsFirst, "G3 first");
		Future<Result> removal = async(() -> removalWaits.removal());
		awaitLockWaiter("G3 first");
		dimensionsFirst.release.countDown();

		assertEquals(200, save.get(60, TimeUnit.SECONDS).status());
		assertError(removal.get(60, TimeUnit.SECONDS), 409, "CONCURRENT_MODIFICATION",
				"otherGroupVersions.dimensions");
		assertEquals(2, spaces(removalWaits.id(), "dimensions"));
		JsonNode fresh = checklists.readOk(saleA, removalWaits.id()).body();
		ObjectNode g1 = ChecklistApiTestSupport.input(fresh, "identification");
		((ArrayNode) g1.path("spaces")).remove(1);
		Result retried = checklists.put(saleA, removalWaits.id(), "identification",
				ChecklistApiTestSupport.version(fresh, "identification"), g1, null,
				ChecklistApiTestSupport.otherVersions(fresh));
		assertEquals(200, retried.status(), retried.rawBody());
		assertEquals(1, spaces(removalWaits.id(), "dimensions"));

		Race saveWaits = removalRace();
		Pause removalFirst = arm(ACTIVITY, saveWaits.leadId());
		Future<Result> firstRemoval = async(() -> saveWaits.removal());
		awaitReached(removalFirst, "G1 first");
		Future<Result> lateSave = async(() -> checklists.put(saleA, saveWaits.id(), "dimensions",
				saveWaits.versionOf("dimensions"), saveWaits.dimensions(), null, null));
		awaitLockWaiter("G1 first");
		removalFirst.release.countDown();

		assertEquals(200, firstRemoval.get(60, TimeUnit.SECONDS).status());
		assertError(lateSave.get(60, TimeUnit.SECONDS), 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		assertEquals(1, spaces(saveWaits.id(), "dimensions"));
	}

	@Test
	void scopeSwitches_againstSavesOfWhatTheyClear_inBothOrders() throws Exception {
		for (boolean switchFirst : List.of(false, true)) {
			String context = "PARTS to WHOLE, switch first " + switchFirst;
			Race race = scopeRace("PARTS", "condition", c -> {
				set(c, "sharedKitchen", checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
				set(c, "sharedBathroom", checklists.notApplicable("No shared bathroom", false));
			});
			ObjectNode condition = ChecklistApiTestSupport.input(race.read(), "condition");
			set(condition, "sharedKitchen", checklists.value(checklists.areaCheck("NOT_ENTERED", null, null,
					"Locked")));
			ObjectNode whole = ChecklistApiTestSupport.input(race.read(), "identification");
			whole.put("rentalScope", "WHOLE");
			((ArrayNode) whole.path("spaces")).remove(1);

			assertRaceOfScope(race, "condition", condition, whole, switchFirst, context);

			JsonNode kitchen = checklists.readOk(saleA, race.id()).body().path("groups").path("condition")
					.path("content").path("sharedKitchen");
			assertTrue(switchFirst ? kitchen.isNull() : "NOT_ENTERED".equals(kitchen.path("value").path("result")
					.asString()), context + ": " + kitchen);
		}
		for (boolean switchFirst : List.of(false, true)) {
			String context = "WHOLE to PARTS, switch first " + switchFirst;
			Race race = scopeRace("WHOLE", "terms", c -> set(c, "spaces.0.monthlyRent",
					checklists.sourced(checklists.rent(12_000_000), "OWNER_FORM")));
			ObjectNode terms = ChecklistApiTestSupport.input(race.read(), "terms");
			ObjectNode deposit = checklists.object();
			deposit.put("basis", "MONTHS");
			deposit.put("months", 2);
			deposit.putNull("amount");
			set(terms, "spaces.0.deposit", checklists.sourced(deposit, "OBSERVED_ON_SITE"));
			ObjectNode parts = ChecklistApiTestSupport.input(race.read(), "identification");
			parts.put("rentalScope", "PARTS");
			((ArrayNode) parts.path("spaces")).add(checklists.space(checklists.newId(), "P109"));

			assertRaceOfScope(race, "terms", terms, parts, switchFirst, context);

			JsonNode rent = checklists.readOk(saleA, race.id()).body().path("groups").path("terms").path("content")
					.path("spaces").path(0).path("monthlyRent");
			assertEquals(switchFirst ? "UNKNOWN" : "VALUE", rent.path("state").asString(), context);
		}
	}

	/**
	 * The group save and the G1 scope switch from one read: whichever runs first pauses with its rows locked while the
	 * other is proven waiting; the second answers 409 on the version that moved (the switch cascades into the group).
	 */
	private void assertRaceOfScope(Race race, String group, ObjectNode groupBody, ObjectNode switchBody,
			boolean switchFirst, String context) throws Exception {
		Pause pause = arm(ACTIVITY, race.leadId());
		Callable<Result> saveCall = () -> checklists.put(saleA, race.id(), group, race.versionOf(group), groupBody,
				null, null);
		Callable<Result> switchCall = () -> checklists.put(saleA, race.id(), "identification",
				race.versionOf("identification"), switchBody, null, ChecklistApiTestSupport.otherVersions(race.read()));

		Future<Result> first = async(switchFirst ? switchCall : saveCall);
		awaitReached(pause, context);
		Future<Result> second = async(switchFirst ? saveCall : switchCall);
		awaitLockWaiter(context);
		pause.release.countDown();

		Result firstResult = first.get(60, TimeUnit.SECONDS);
		assertEquals(200, firstResult.status(), context + ": " + firstResult.rawBody());
		assertError(second.get(60, TimeUnit.SECONDS), 409, "CONCURRENT_MODIFICATION",
				switchFirst ? "expectedVersion" : "otherGroupVersions." + group);
	}

	// ==================== Review Focus 3 ====================

	@Test
	void save_sameBodyTwiceInParallel_storesOnce() throws Exception {
		Setup setup = setup(ChecklistCommand.SAVE, LeadCommand.HOLD);
		assertStoredOnce(setup, "parts", setup.parts(), setup.partsVersion());
	}

	@Test
	void save_partsConditionTwiceInParallel_storesOnce() throws Exception {
		Race race = scopeRace("PARTS", null, null);
		ObjectNode condition = ChecklistApiTestSupport.input(race.read(), "condition");
		set(condition, "sharedKitchen", checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
		set(condition, "sharedBathroom", checklists.notApplicable("No shared bathroom", false));
		Setup setup = new Setup(new Visit(race.leadId(), null, null), race.id(), null, saleA, null, 0);

		assertStoredOnce(setup, "condition", condition, race.versionOf("condition"));
	}

	// ==================== takeovers ====================

	@Test
	void concurrentTakeovers_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Visit visit = checklists.visited(leader, saleA);
			String id = checklists.started(saleA, visit.appointmentId());
			checklists.reassign(leader, visit.leadId(), saleB);
			long version = checklists.checklistVersion(id);

			List<Result> results = race(i -> checklists.takeOver(saleB, id, version, "New assignee",
					visit.arrivalDate(), "Leader handed it over"));

			String context = "round " + round + ": " + statuses(results);
			Result loser = results.get(1 - winnerOf(results, 201, context));
			assertEquals(409, loser.status(), context + ": " + loser.rawBody());
			String field = loser.body().path("errors").path(0).path("field").asString();
			assertTrue(Set.of("expectedVersion", "holder").contains(field), context + ": " + loser.rawBody());
			assertEquals(1, checklists.rows("select count(*) from survey_checklist_takeovers where checklist_id = ?",
					id), context);
			assertEquals(1, checklists.activities(visit.leadId(), "CHECKLIST_TAKEN_OVER").size(), context);
		}
	}

	// ==================== helpers ====================

	/** A lead and its draft (or none for a start), set up for the checklist command against the lead command. */
	private Setup setup(ChecklistCommand command, LeadCommand leadCommand) throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = null;
		ObjectNode parts = null;
		long partsVersion = 0;
		if (command != ChecklistCommand.START) {
			id = checklists.started(saleA, visit.appointmentId());
			String spaceId = checklists.newId();
			JsonNode saved = checklists.saved(saleA, id, "identification", c -> c.setAll(
					checklists.identification("WHOLE", checklists.space(spaceId, "P101"))));
			parts = ChecklistApiTestSupport.input(saved, "parts");
			set(parts, "accessRoute", checklists.value("Alley next to the market"));
			partsVersion = ChecklistApiTestSupport.version(saved, "parts");
		}
		// The takeover's new assignee books the re-survey, so they held it at the arrival they record
		TestUser actor = saleA;
		if (command == ChecklistCommand.TAKEOVER) {
			checklists.reassign(leader, visit.leadId(), saleB);
			actor = saleB;
		}
		String resurveyId = null;
		if (leadCommand == LeadCommand.VISIT) {
			SurveyApiTestSupport.ok(survey.moveTo(leader, visit.leadId(), "ON_HOLD"));
			SurveyApiTestSupport.ok(survey.moveTo(leader, visit.leadId(), "CONTACTED"));
			Map<String, Object> body = survey.booking(visit.leadId(), SurveyApiTestSupport.startIn(30));
			body.put("resurveyReason", "Owner rebuilt the kitchen");
			Result booked = survey.book(actor, visit.leadId(), body);
			assertEquals(201, booked.status(), booked.rawBody());
			resurveyId = booked.body().path("survey").path("appointments").path(0).path("id").asString();
			survey.shiftBack(resurveyId, Duration.ofDays(2));
		}
		return new Setup(visit, id, resurveyId, actor, parts, partsVersion);
	}

	private Result send(ChecklistCommand command, Setup setup) throws Exception {
		return switch (command) {
			case SAVE -> checklists.put(saleA, setup.checklistId(), "parts", setup.partsVersion(), setup.parts(), null,
					null);
			case TAKEOVER -> checklists.takeOver(saleB, setup.checklistId(), setup.visit());
			case START -> checklists.start(saleA, setup.visit().appointmentId(), null);
		};
	}

	private Result send(LeadCommand command, Setup setup) throws Exception {
		String leadId = setup.visit().leadId();
		return switch (command) {
			case REASSIGN -> api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/assign",
					Map.of("assigneeUserId", saleC.userId(), "expectedVersion", survey.version(leadId)));
			case HOLD -> survey.moveTo(leader, leadId, "ON_HOLD");
			case REQUIREMENT -> {
				Map<String, Object> change = new LinkedHashMap<>();
				change.put("expectedVersion", survey.version(leadId));
				change.put("newIntent", "SHORT_TERM_RENT");
				change.put("askingPrice", null);
				change.put("reason", "Owner changed plans");
				change.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE),
						"Owner called"));
				yield api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes", change);
			}
			case VISIT -> {
				Instant start = jdbc.queryForObject("select start_at from survey_appointments where id = ?",
						Timestamp.class, setup.resurveyId()).toInstant();
				yield checklists.recordVisit(setup.actor(), leadId, setup.resurveyId(), start);
			}
		};
	}

	private void assertCommitted(ChecklistCommand command, Setup setup, String context) {
		switch (command) {
			case SAVE -> assertEquals(setup.partsVersion() + 1, groupVersion(setup.checklistId(), "PARTS"), context);
			case TAKEOVER -> assertEquals(saleB.userId(), jdbc.queryForObject("select holder_user_id from "
					+ "survey_checklists where id = ?", String.class, setup.checklistId()), context);
			case START -> assertEquals(1, checklists.rows("select count(*) from survey_checklists where lead_id = ?",
					setup.visit().leadId()), context);
		}
	}

	private void assertNotWritten(ChecklistCommand command, Setup setup, String context) {
		switch (command) {
			case SAVE -> assertEquals(setup.partsVersion(), groupVersion(setup.checklistId(), "PARTS"), context);
			case TAKEOVER -> assertEquals(0, checklists.rows("select count(*) from survey_checklist_takeovers where "
					+ "checklist_id = ?", setup.checklistId()), context);
			case START -> assertEquals(0, checklists.rows("select count(*) from survey_checklists where lead_id = ?",
					setup.visit().leadId()), context);
		}
	}

	/** The refusal after the lead command: not visible after a reassignment, else read-only or not allowed. */
	private static void assertRefused(ChecklistCommand command, LeadCommand leadCommand, Result result,
			String context) {
		if (leadCommand == LeadCommand.REASSIGN) {
			assertError(result, 404, "NOT_FOUND", null);
			return;
		}
		assertEquals(409, result.status(), context + ": " + result.rawBody());
		assertEquals(command == ChecklistCommand.START ? "CHECKLIST_NOT_ALLOWED" : "CHECKLIST_READ_ONLY",
				result.body().path("code").asString(), context + ": " + result.rawBody());
	}

	/** Both identical PUTs: the first paused with its group row locked, the second proven waiting on it. */
	private void assertStoredOnce(Setup setup, String group, ObjectNode content, long version) throws Exception {
		String key = group.toUpperCase();
		int saves = checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ? and group_key "
				+ "= ?", setup.checklistId(), key);
		int history = checklists.activities(setup.visit().leadId(), "CHECKLIST_SAVED").size();
		Pause pause = arm(ACTIVITY, setup.visit().leadId());

		Future<Result> first = async(() -> checklists.put(saleA, setup.checklistId(), group, version, content, null,
				null));
		awaitReached(pause, group);
		Future<Result> second = async(() -> checklists.put(saleA, setup.checklistId(), group, version, content, null,
				null));
		awaitLockWaiter(group);
		pause.release.countDown();

		assertEquals(200, first.get(60, TimeUnit.SECONDS).status());
		assertEquals(200, second.get(60, TimeUnit.SECONDS).status());
		assertEquals(version + 1, groupVersion(setup.checklistId(), key));
		assertEquals(saves + 1, checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ? "
				+ "and group_key = ?", setup.checklistId(), key));
		assertEquals(history + 1, checklists.activities(setup.visit().leadId(), "CHECKLIST_SAVED").size());
	}

	/** A PARTS draft with P101 and P102 whose G3 holds a P102 entry, and both race bodies from one read. */
	private record Race(String leadId, String id, JsonNode read, ObjectNode dimensions, ObjectNode removalBody,
			ChecklistApiTestSupport checklists, TestUser caller) {

		long versionOf(String group) {
			return ChecklistApiTestSupport.version(read, group);
		}

		Result removal() throws Exception {
			return checklists.put(caller, id, "identification", versionOf("identification"), removalBody, null,
					ChecklistApiTestSupport.otherVersions(read));
		}
	}

	private Race removalRace() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(checklists.newId(), "P101"), checklists.space(checklists.newId(), "P102"))));
		JsonNode read = checklists.saved(saleA, id, "dimensions", c -> set(c, "spaces.1.area",
				checklists.sourced(new BigDecimal("25"), "MEASURED")));
		ObjectNode dimensions = ChecklistApiTestSupport.input(read, "dimensions");
		set(dimensions, "spaces.1.bedrooms", checklists.sourced(1, "OBSERVED_ON_SITE"));
		ObjectNode removal = ChecklistApiTestSupport.input(read, "identification");
		((ArrayNode) removal.path("spaces")).remove(1);
		return new Race(visit.leadId(), id, read, dimensions, removal, checklists, saleA);
	}

	/** A draft in the scope (PARTS: P101 and P102; WHOLE: P101) after the given save of a group, and its read. */
	private Race scopeRace(String scope, String group, Consumer<ObjectNode> edit)
			throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		ObjectNode[] spaces = "PARTS".equals(scope)
				? new ObjectNode[] { checklists.space(checklists.newId(), "P101"),
						checklists.space(checklists.newId(), "P102") }
				: new ObjectNode[] { checklists.space(checklists.newId(), "P101") };
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification(scope, spaces)));
		JsonNode read = group == null ? checklists.readOk(saleA, id).body() : checklists.saved(saleA, id, group, edit);
		return new Race(visit.leadId(), id, read, null, null, checklists, saleA);
	}

	private Pause arm(String point, String leadId) {
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
			if (checklists.rows("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = "
					+ "current_database()") > 0) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(context + ": the second command never waited on a lock");
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

	@FunctionalInterface
	private interface Call {
		Result send(int index) throws Exception;
	}

	private static int winnerOf(List<Result> results, int okStatus, String context) {
		List<Integer> statuses = results.stream().map(Result::status).toList();
		assertTrue(statuses.contains(okStatus), context);
		assertTrue(statuses.stream().noneMatch(status -> status >= 500), context);
		return statuses.indexOf(okStatus);
	}

	private static String statuses(List<Result> results) {
		return results.stream().map(result -> result.status() + " " + result.body().path("code").asString())
				.toList().toString();
	}

	private long groupVersion(String checklistId, String group) {
		return jdbc.queryForObject("select version from survey_checklist_groups where checklist_id = ? and "
				+ "group_key = ?", Long.class, checklistId, group);
	}

	private int spaces(String checklistId, String group) throws Exception {
		return checklists.readOk(saleA, checklistId).body().path("groups").path(group).path("content")
				.path("spaces").size();
	}

	private int historyRows(String leadId) {
		return checklists.rows("select count(*) from owner_lead_activities where lead_id = ?", leadId);
	}
}
