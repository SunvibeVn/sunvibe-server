package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.shared.media.MediaReadBudgetLimits;
import vn.sunvibe.survey.core.application.shared.media.MediaReadBudgets;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;

/**
 * The hourly read budgets on PostgreSQL (survey-media spec A-26; SM-31 list part; plan Task A16): the list budget of
 * 1,200 refuses the 1,201st read with 429 and no access row, one BUDGET_EXCEEDED event per user, kind and hour, the
 * weighted carry-over of the previous hour, one budget per user whatever the instances, counted outside the list's
 * snapshot. Needs Docker.
 */
class MediaReadBudgetPostgresTest extends MediaApiTestBase {

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
	private MediaCountersRepository counters;

	@Autowired
	private SecurityEventRecorder events;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private MediaTestStore store;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser leader;
	private final ExecutorService pool = Executors.newFixedThreadPool(8);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0980");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		leader = survey.user("Budget Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		survey.cleanUp();
	}

	@Test
	void list1201st_429BudgetNoRowNoStoreCall() throws Exception {
		TestUser sale = survey.user("Budget Sale One", "SALE");
		Draft draft = draft(sale);
		setCount(sale, Instant.now(), 1_200);

		Result refused = media.list(sale, draft.id(), null);

		assertEquals(429, refused.status(), refused.rawBody());
		assertEquals("RATE_LIMITED", refused.body().path("code").asString());
		assertEquals("budget", refused.body().path("errors").path(0).path("field").asString());
		assertTrue(Long.parseLong(refused.header("Retry-After")) > 0);
		assertEquals(0, listRows(sale, draft.id()), "a refused read writes no access row");
	}

	@Test
	void firstRefusal_oneEvent_parallelRefusalsNone() throws Exception {
		TestUser sale = survey.user("Budget Sale Parallel", "SALE");
		Draft draft = draft(sale);
		setCount(sale, Instant.now(), 1_200);

		List<Callable<Result>> calls = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			calls.add(() -> media.list(sale, draft.id(), null));
		}
		for (Future<Result> future : pool.invokeAll(calls, 60, TimeUnit.SECONDS)) {
			assertEquals(429, future.get().status());
		}

		List<java.util.Map<String, Object>> rows = jdbc.queryForList("select occurred_at, resource_type, resource_id,"
				+ " request_id from survey_security_events where user_id = ? and outcome = 'BUDGET_EXCEEDED'",
				sale.userId());
		assertEquals(1, rows.size(), rows + " / " + jdbc.queryForList("select * from survey_media_read_budgets"
				+ " where user_id = ?", sale.userId()));
	}

	@Test
	void weightedCarryOver_atHourStartAndHalfPast() {
		TestUser sale = userWithoutDraft("Budget Sale Carry");
		Instant hour = ReadBudgetArithmetic.hourStart(Instant.now()).minus(Duration.ofHours(3));
		setCount(sale, hour.minus(Duration.ofHours(1)), 1_200);

		MediaReadBudgets atHourStart = budgets(hour);
		assertThrows(MediaRateLimitedException.class, () -> count(atHourStart, sale),
				"the whole previous hour still weighs at the hour start: limit 0, nothing counted");
		assertEquals(0, count(sale, hour));

		MediaReadBudgets halfPast = budgets(hour.plus(Duration.ofMinutes(30)));
		setCount(sale, hour, 599);
		count(halfPast, sale);
		assertEquals(600, count(sale, hour), "1200 - floor(1200 x 0.5) = 600 allowed at half past");
		assertThrows(MediaRateLimitedException.class, () -> count(halfPast, sale));
		assertEquals(600, count(sale, hour));
	}

	/** survey-media spec A-26 and 9 (configuration), code review M2 (SV-069): the configured budget is the one enforced. */
	@Test
	void configuredListBudgetOfTwo_thirdListRefused() {
		TestUser sale = userWithoutDraft("Budget Sale Configured");
		Instant hour = ReadBudgetArithmetic.hourStart(Instant.now()).minus(Duration.ofHours(5));
		MediaReadBudgets budgets = budgets(hour, new MediaReadBudgetLimits(600, 1_200, 6_000, 2));

		count(budgets, sale);
		count(budgets, sale);

		MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class, () -> count(budgets, sale));
		assertEquals("budget", refused.getField());
		assertEquals(2, count(sale, hour));
	}

	@Test
	void twoInstancesShareBudget() {
		TestUser sale = userWithoutDraft("Budget Sale Shared");
		Instant now = Instant.now();
		setCount(sale, now, 1_199);
		MediaReadBudgets first = budgets(now);
		MediaReadBudgets second = budgets(now);

		count(first, sale);

		assertThrows(MediaRateLimitedException.class, () -> count(second, sale),
				"the budget is per user in the database, not per instance");
	}

	@Test
	void upsert_runsReadCommittedOutsideSnapshot() {
		TestUser sale = userWithoutDraft("Budget Sale Snapshot");
		Instant now = Instant.now();
		MediaReadBudgets budgets = budgets(now);
		TransactionTemplate snapshot = new TransactionTemplate(transactionManager);
		snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

		snapshot.executeWithoutResult(status -> {
			count(budgets, sale);
			assertEquals(1, count(sale, ReadBudgetArithmetic.hourStart(now)),
					"committed in its own transaction while the snapshot is still open");
		});
	}

	// ==================== content reads (SM-31) ====================

	@Test
	void full601st_preview1201st_thumbnail6001st_429BudgetNoRowNoStoreCall() throws Exception {
		TestUser sale = survey.user("Budget Sale Content", "SALE");
		Draft draft = draft(sale);
		String id = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 1),
				MediaApiTestSupport.bytes(2000, 2), MediaApiTestSupport.bytes(1000, 3));
		Map<String, Long> budgets = Map.of("FULL", 600L, "PREVIEW", 1_200L, "THUMBNAIL", 6_000L);
		budgets.forEach((kind, budget) -> setCount(sale, Instant.now(), kind, budget));
		store.reset();

		for (String variant : List.of("full", "preview", "thumbnail")) {
			Result refused = media.read(sale, id, variant);
			assertEquals(429, refused.status(), variant + " " + refused.rawBody());
			assertEquals("budget", refused.body().path("errors").path(0).path("field").asString());
			assertTrue(Long.parseLong(refused.header("Retry-After")) > 0);
		}
		assertEquals(0, store.servingReads.get(), "no store call");
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_access_logs where user_id = ? and"
				+ " resource_type <> 'CHECKLIST' and resource_type <> 'SURVEY_BLOCK'", Integer.class, sale.userId()));
	}

	@Test
	void parallelReads_overshootAtMostUserCap() throws Exception {
		TestUser sale = survey.user("Budget Sale Overshoot", "SALE");
		Draft draft = draft(sale);
		String id = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 4),
				MediaApiTestSupport.bytes(2000, 5), MediaApiTestSupport.bytes(1000, 6));
		setCount(sale, Instant.now(), "PREVIEW", 1_199L);

		List<Callable<Result>> calls = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			calls.add(() -> media.read(sale, id, "preview"));
		}
		long passed = 0;
		for (Future<Result> future : pool.invokeAll(calls, 60, TimeUnit.SECONDS)) {
			if (future.get().status() == 200) {
				passed++;
			}
		}

		assertTrue(passed >= 1 && passed <= 6, "the budget may be passed by at most the user's 6 reads: " + passed);
		assertEquals(1, passed, "counted exactly per row in the database");
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft(TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private TestUser userWithoutDraft(String name) {
		try {
			return survey.user(name, "SALE");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private MediaReadBudgets budgets(Instant at) {
		return budgets(at, MediaReadBudgetLimits.SPEC);
	}

	private MediaReadBudgets budgets(Instant at, MediaReadBudgetLimits limits) {
		Instant fixed = at.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
		return new MediaReadBudgets(counters, events, () -> fixed, transactionManager, limits);
	}

	private void count(MediaReadBudgets budgets, TestUser sale) {
		budgets.count(sale.userId(), "01JBUDGETLEAD0000000000001", MediaCounterKind.LIST,
				SecurityResourceType.MEDIA_SET, "01JBUDGETCHECK000000000001");
	}

	private void setCount(TestUser sale, Instant at, long count) {
		setCount(sale, at, "LIST", count);
	}

	private void setCount(TestUser sale, Instant at, String kind, long count) {
		jdbc.update("insert into survey_media_read_budgets (user_id, hour_start, kind, read_count)"
				+ " values (?, ?, ?, ?) on conflict (user_id, hour_start, kind) do update set read_count = ?",
				sale.userId(), Timestamp.from(ReadBudgetArithmetic.hourStart(at)), kind, count, count);
	}

	private long count(TestUser sale, Instant hourStart) {
		List<Long> counts = jdbc.queryForList("select read_count from survey_media_read_budgets where user_id = ?"
				+ " and hour_start = ? and kind = 'LIST'", Long.class, sale.userId(), Timestamp.from(hourStart));
		return counts.isEmpty() ? 0 : counts.get(0);
	}

	private int listRows(TestUser sale, String checklistId) {
		return jdbc.queryForObject("select count(*) from survey_access_logs where user_id = ? and resource_type ="
				+ " 'MEDIA_LIST' and resource_id = ?", Integer.class, sale.userId(), checklistId);
	}
}
