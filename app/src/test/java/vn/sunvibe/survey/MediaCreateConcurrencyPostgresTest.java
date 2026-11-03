package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;

/**
 * Concurrent creates on PostgreSQL (survey-media spec 4.4 step 1, 5.4 lock order, A-3, A-5; SM-5, SM-9; plan Task
 * A13): the per-user advisory lock and the set lock serialize them, the 100 cap holds under parallel creates, the
 * first creates of a checklist share one set row, a twin create is a replay, and nothing deadlocks. Needs Docker.
 */
class MediaCreateConcurrencyPostgresTest extends MediaApiTestBase {

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
	private DataSource dataSource;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;
	private final ExecutorService pool = Executors.newFixedThreadPool(10);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0972");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Media Concurrency Sale", "SALE");
		leader = survey.user("Media Concurrency Leader", "SALE_LEADER");
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
	void tenParallelCreatesAt99Selected_oneAcceptedNine409() throws Exception {
		String checklistId = draft();
		Instant earlier = Instant.now().minus(Duration.ofHours(2));
		media.insertPhotos(checklistId, sale.userId(), Row.READY, 80, earlier, 1000L, "cap-ready");
		media.insertPhotos(checklistId, sale.userId(), Row.AWAITING_CONTENT, 19, earlier, 1000L, "cap-pending");

		List<Result> results = parallel(10, i -> media.create(sale, checklistId, MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("cap-parallel-" + i))));

		assertEquals(1, results.stream().filter(r -> r.status() == 201).count(), statuses(results));
		List<Result> refused = results.stream().filter(r -> r.status() != 201).toList();
		assertEquals(9, refused.size());
		for (Result result : refused) {
			assertEquals(409, result.status(), result.rawBody());
			assertEquals("MEDIA_LIMIT_REACHED", result.body().path("code").asString());
			assertEquals("selected", result.body().path("errors").path(0).path("field").asString());
		}
		assertEquals(100, selected(checklistId));
	}

	@Test
	void tenParallelFirstCreatesWithoutSetRow_oneSetRowTenPhotosDistinctNumbers() throws Exception {
		String checklistId = draft();

		List<Result> results = parallel(10, i -> media.create(sale, checklistId, MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("first-parallel-" + i))));

		assertTrue(results.stream().allMatch(r -> r.status() == 201), statuses(results));
		assertEquals(1, media.setRows(checklistId));
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), media.photos(checklistId).stream()
				.map(row -> ((Number) row.get("media_no")).intValue()).toList());
		assertEquals(10, media.setVersion(checklistId));
	}

	@Test
	void twoConcurrentIdenticalCreates_one201One200SamePhoto() throws Exception {
		String checklistId = draft();
		Map<String, Object> body = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("identical-twins"));

		List<Future<Result>> futures = new ArrayList<>();
		try (MediaApiTestSupport.HeldLock held = MediaApiTestSupport.holdUserLock(dataSource, jdbc,
				sale.userId())) {
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> media.create(sale, checklistId, body)));
			}
			held.awaitWaiters(2);
		}
		List<Result> results = new ArrayList<>();
		for (Future<Result> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}

		assertEquals(List.of(200, 201), results.stream().map(Result::status).sorted().toList(), statuses(results));
		assertEquals(results.get(0).body().path("media").path("id").asString(),
				results.get(1).body().path("media").path("id").asString());
		assertEquals(1, media.photos(checklistId).size());
	}

	@Test
	void pgStatDeadlocks_unchanged() throws Exception {
		long before = deadlocks();
		String checklistId = draft();

		List<Result> results = parallel(10, i -> media.create(sale, checklistId, MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("deadlock-" + (i % 3)))));
		Thread.sleep(1_500);

		assertTrue(results.stream().allMatch(r -> r.status() == 201 || r.status() == 409), statuses(results));
		assertEquals(3, results.stream().filter(r -> r.status() == 201).count(), statuses(results));
		assertEquals(before, deadlocks());
	}

	// ==================== helpers ====================

	private String draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return checklists.started(sale, visit.appointmentId());
	}

	private List<Result> parallel(int n, Call call) throws Exception {
		List<Callable<Result>> calls = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int index = i;
			calls.add(() -> call.apply(index));
		}
		List<Result> results = new ArrayList<>();
		for (Future<Result> future : pool.invokeAll(calls, 120, TimeUnit.SECONDS)) {
			results.add(future.get());
		}
		return results;
	}

	/** One request of a parallel batch. */
	@FunctionalInterface
	private interface Call {
		Result apply(int index) throws Exception;
	}

	private int selected(String checklistId) {
		return jdbc.queryForObject("select count(*) from survey_media where checklist_id = ? and removed_at is null"
				+ " and status in ('AWAITING_CONTENT', 'RECEIVING', 'PROCESSING', 'READY')", Integer.class,
				checklistId);
	}

	private long deadlocks() {
		jdbc.execute("select pg_stat_clear_snapshot()");
		return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()",
				Long.class);
	}

	private static String statuses(List<Result> results) {
		return results.stream().map(r -> r.status() + " " + r.rawBody()).toList().toString();
	}
}
