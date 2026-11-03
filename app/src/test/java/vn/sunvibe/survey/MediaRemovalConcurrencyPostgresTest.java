package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
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
import vn.sunvibe.survey.MediaApiTestSupport.Row;

/**
 * Removal against the content commit and the G1 cascade (survey-media spec 5.4, 4.7; SM-11 race; plan Task A19): in
 * both orders, in a loop, one consistent outcome and no deadlock. Needs Docker.
 */
class MediaRemovalConcurrencyPostgresTest extends MediaApiTestBase {

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
	private final ExecutorService pool = Executors.newFixedThreadPool(8);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0989");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Race Sale", "SALE");
		leader = survey.user("Race Leader", "SALE_LEADER");
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
	void removalRacingCommit_bothOrdersInLoop_oneOutcomeNoDeadlock() throws Exception {
		String checklistId = draft();
		for (int round = 0; round < 8; round++) {
			byte[] bytes = MediaApiTestSupport.bytes(4000, 100 + round);
			String id = media.created(sale, checklistId, bytes);
			try (RawUpload upload = media.openUpload(sale, id, bytes.length, MediaApiTestSupport.digest(bytes))) {
				upload.send(Arrays.copyOf(bytes, bytes.length - 1));
				awaitStatus(id, "RECEIVING");
				Callable<String> commit = () -> {
					upload.send(Arrays.copyOfRange(bytes, bytes.length - 1, bytes.length));
					return upload.answer(20_000).get(":status");
				};
				Callable<String> remove = () -> String.valueOf(media.remove(sale, id, null).status());
				List<Future<String>> futures = round % 2 == 0 ? List.of(pool.submit(commit), pool.submit(remove))
						: List.of(pool.submit(remove), pool.submit(commit));
				String first = futures.get(0).get(30, TimeUnit.SECONDS);
				String second = futures.get(1).get(30, TimeUnit.SECONDS);
				String commitStatus = round % 2 == 0 ? first : second;
				String removeStatus = round % 2 == 0 ? second : first;

				assertEquals("200", removeStatus, "round " + round);
				assertTrue(List.of("202", "409").contains(commitStatus), "round " + round + ": " + commitStatus);
			}
			Map<String, Object> row = media.row(id);
			assertNotNull(row.get("removed_at"), "round " + round);
			assertNotEquals("READY", row.get("status"), "round " + round);
		}
	}

	@Test
	void pgStatDeadlocks_unchanged() throws Exception {
		long before = deadlocks();
		String checklistId = draft();
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			ids.add(media.insertPhoto(checklistId, sale.userId(), i % 2 == 0 ? Row.READY : Row.PROCESSING,
					Instant.now().minus(Duration.ofMinutes(5)), MediaApiTestSupport.sha(MediaApiTestSupport.newKey())));
		}
		List<Callable<Result>> calls = new ArrayList<>();
		for (String id : ids) {
			calls.add(() -> media.remove(sale, id, null));
		}
		calls.add(() -> media.create(sale, checklistId, MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()))));
		calls.add(() -> media.list(sale, checklistId, null));

		for (Future<Result> future : pool.invokeAll(calls, 60, TimeUnit.SECONDS)) {
			assertTrue(future.get().status() < 500, future.get().rawBody());
		}
		Thread.sleep(1500);
		assertEquals(before, deadlocks(), "no deadlock in PostgreSQL");
	}

	// ==================== helpers ====================

	private String draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return checklists.started(sale, visit.appointmentId());
	}

	private long deadlocks() {
		return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()",
				Long.class);
	}

	private void awaitStatus(String id, String status) throws InterruptedException {
		long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!status.equals(media.row(id).get("status")) && System.nanoTime() < until) {
			Thread.sleep(50);
		}
		assertEquals(status, media.row(id).get("status"));
	}
}
