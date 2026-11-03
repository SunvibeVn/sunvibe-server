package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.RawUpload;

/**
 * Concurrent content uploads on PostgreSQL (survey-media spec 4.4 step 2, 5.4; SM-9; plan Task A14): the conditional
 * claim lets one upload of a photo in, the other answers 409 MEDIA_UPLOAD_IN_PROGRESS, one source is stored, and
 * concurrent commits on one checklist never deadlock. Needs Docker.
 */
class MediaCommitConcurrencyPostgresTest extends MediaApiTestBase {

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
	private final ExecutorService pool = Executors.newFixedThreadPool(6);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0977");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Commit Concurrency Sale", "SALE");
		leader = survey.user("Commit Concurrency Leader", "SALE_LEADER");
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
	void twoConcurrentPuts_one202One409InProgress_oneSource() throws Exception {
		String checklistId = draft();
		byte[] bytes = MediaApiTestSupport.bytes(256 * 1024, 70);
		String id = media.created(sale, checklistId, bytes);

		Result second;
		Map<String, String> first;
		try (RawUpload upload = media.openUpload(sale, id, bytes.length, MediaApiTestSupport.digest(bytes))) {
			upload.send(Arrays.copyOf(bytes, 1024));
			awaitStatus(id, "RECEIVING");
			second = media.put(sale, id, bytes);
			upload.send(Arrays.copyOfRange(bytes, 1024, bytes.length));
			first = upload.answer(30_000);
		}

		assertEquals("202", first.get(":status"), first.toString());
		assertEquals(409, second.status(), second.rawBody());
		assertEquals("MEDIA_UPLOAD_IN_PROGRESS", second.body().path("code").asString());
		assertEquals(1, sources(id));
	}

	@Test
	void parallelPutsOfOnePhoto_oneClaimWins() throws Exception {
		String checklistId = draft();
		byte[] bytes = MediaApiTestSupport.bytes(512 * 1024, 71);
		String id = media.created(sale, checklistId, bytes);

		List<Result> results = parallel(2, i -> media.put(sale, id, bytes));

		for (Result result : results) {
			assertTrue(result.status() == 202 || (result.status() == 409
					&& "MEDIA_UPLOAD_IN_PROGRESS".equals(result.body().path("code").asString())), result.rawBody());
		}
		assertTrue(results.stream().anyMatch(r -> r.status() == 202));
		assertEquals("PROCESSING", status(id));
		assertEquals(1, sources(id));
	}

	@Test
	void pgStatDeadlocks_unchanged() throws Exception {
		long before = deadlocks();
		String checklistId = draft();
		List<byte[]> files = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			byte[] bytes = MediaApiTestSupport.bytes(128 * 1024, 80 + i);
			files.add(bytes);
			ids.add(media.created(sale, checklistId, bytes));
		}

		List<Result> results = parallel(3, i -> media.put(sale, ids.get(i), files.get(i)));
		Thread.sleep(1_500);

		assertTrue(results.stream().allMatch(r -> r.status() == 202), results.stream().map(Result::rawBody)
				.toList().toString());
		assertEquals(before, deadlocks());
	}

	// ==================== helpers ====================

	private String draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return checklists.started(sale, visit.appointmentId());
	}

	@FunctionalInterface
	private interface Call {
		Result apply(int index) throws Exception;
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

	private String status(String mediaId) {
		return jdbc.queryForObject("select status from survey_media where id = ?", String.class, mediaId);
	}

	private int sources(String mediaId) {
		return jdbc.queryForObject("select count(*) from survey_media where id = ? and source_storage_key is not"
				+ " null", Integer.class, mediaId);
	}

	private void awaitStatus(String mediaId, String expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while (!expected.equals(status(mediaId))) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError(mediaId + " never became " + expected);
			}
			Thread.sleep(20);
		}
	}

	private long deadlocks() {
		return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()",
				Long.class);
	}
}
