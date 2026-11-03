package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.node.ArrayNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;

/**
 * The hold against the holder's removal and the G1 cascade (survey-media spec A-23, 5.4; SM-30 races; plan Task A20):
 * in both orders, in a loop, one consistent outcome, never a deletion requested while the photo is held, and no
 * deadlock. Needs Docker.
 */
class MediaHoldConcurrencyPostgresTest extends MediaApiTestBase {

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
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;
	private TestUser admin;
	private final ExecutorService pool = Executors.newFixedThreadPool(8);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0991");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Hold Race Sale", "SALE");
		leader = survey.user("Hold Race Leader", "SALE_LEADER");
		admin = survey.user("Hold Race Admin", "ADMIN");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void holdVsRemoval_bothOrdersInLoop_oneOutcome_neverDeletionWhileHeld() throws Exception {
		String checklistId = draft().id();
		for (int round = 0; round < 10; round++) {
			String id = photo(checklistId, null, null);
			Callable<Result> hold = () -> hold(id);
			Callable<Result> remove = () -> media.remove(sale, id, null);
			List<Result> results = race(round, hold, remove);

			int holdStatus = results.get(0).status();
			assertEquals(200, results.get(1).status(), "the holder's removal goes through: " + results.get(1).rawBody());
			assertTrue(holdStatus == 200 || holdStatus == 409, "round " + round + ": " + holdStatus);
			assertHeldRule(id, holdStatus == 200);
		}
	}

	@Test
	void holdVsCascade_bothOrdersInLoop_sameRule() throws Exception {
		for (int round = 0; round < 6; round++) {
			Draft draft = parts();
			String id = photo(draft.id(), draft.space2(), "P102");
			Callable<Result> hold = () -> hold(id);
			Callable<Result> save = () -> checklists.save(sale, draft.id(), "identification", c -> {
				ArrayNode spaces = (ArrayNode) c.path("spaces");
				spaces.remove(1);
			});
			List<Result> results = race(round, hold, save);

			int holdStatus = results.get(0).status();
			assertEquals(200, results.get(1).status(), results.get(1).rawBody());
			assertTrue(holdStatus == 200 || holdStatus == 409, "round " + round + ": " + holdStatus);
			assertHeldRule(id, holdStatus == 200);
		}
	}

	@Test
	void pgStatDeadlocks_unchanged() throws Exception {
		long before = deadlocks();
		String checklistId = draft().id();
		List<Callable<Result>> calls = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			String id = photo(checklistId, null, null);
			calls.add(() -> hold(id));
			calls.add(() -> media.remove(sale, id, null));
			calls.add(() -> checklists.api().post(admin, MediaApiTestSupport.MEDIA + "/" + id + "/hold-release",
					Map.of()));
		}
		for (Future<Result> future : pool.invokeAll(calls, 60, TimeUnit.SECONDS)) {
			assertTrue(future.get().status() < 500, future.get().rawBody());
		}
		Thread.sleep(1500);
		assertEquals(before, deadlocks());
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id, String space2) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()), null);
	}

	private Draft parts() throws Exception {
		Draft draft = draft();
		String space2 = checklists.newId();
		checklists.saved(sale, draft.id(), "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(checklists.newId(), "P101"), checklists.space(space2, "P102"))));
		return new Draft(draft.visit(), draft.id(), space2);
	}

	private String photo(String checklistId, String spaceIdOrNull, String codeOrNull) {
		return media.insertPhoto(checklistId, sale.userId(), Row.READY, Instant.now().minus(Duration.ofMinutes(5)),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()), spaceIdOrNull, codeOrNull,
				spaceIdOrNull == null ? "DETAIL" : "KITCHEN");
	}

	private Result hold(String id) throws Exception {
		return checklists.api().post(admin, MediaApiTestSupport.MEDIA + "/" + id + "/hold",
				Map.of("reason", "SECURITY_INCIDENT"));
	}

	/** Both calls at once, submitted in the order of the round; the answers as [first, second] callable. */
	private List<Result> race(int round, Callable<Result> first, Callable<Result> second) throws Exception {
		Future<Result> a;
		Future<Result> b;
		if (round % 2 == 0) {
			a = pool.submit(first);
			b = pool.submit(second);
		} else {
			b = pool.submit(second);
			a = pool.submit(first);
		}
		return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
	}

	/** spec A-23: removed and held → no deletion request or job; removed and not held → requested with its job. */
	private void assertHeldRule(String id, boolean held) {
		Map<String, Object> row = media.row(id);
		assertTrue(row.get("removed_at") != null);
		if (held) {
			assertNull(row.get("content_delete_requested_at"));
			assertEquals(0, media.jobs(id).size());
		} else {
			assertTrue(row.get("content_delete_requested_at") != null);
			assertEquals(1, media.jobs(id).size());
		}
	}

	private long deadlocks() {
		return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()",
				Long.class);
	}
}
