package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.service.media.MediaProcessingWorker;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaWorkCounters;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;
import vn.sunvibe.survey.core.infrastructure.media.processor.IsolatedProcessorClient;
import vn.sunvibe.survey.media.FakeProcessor;

/**
 * The processing worker on PostgreSQL (survey-media spec 4.4 step 3, A-7, A-18, A-27; SM-3, SM-4, SM-18, SM-32 worker
 * parts; plan Task A15): the real repositories, the local store, a scripted scanner and a scripted or fake processor.
 * Pending jobs of other test classes are closed first, so each test sees only its own. Needs Docker.
 */
class MediaWorkerPostgresTest extends MediaApiTestBase {

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
	private SurveyMediaJobDomainRepository jobs;

	@Autowired
	private SurveyMediaDomainRepository mediaRepository;

	@Autowired
	private SurveyMediaSetDomainRepository sets;

	@Autowired
	private SurveyMediaVariantDomainRepository variants;

	@Autowired
	private SurveyChecklistDomainRepository checklistRepository;

	@Autowired
	private MediaObjectStore store;

	@Autowired
	private IntakeHealthRepository health;

	@Autowired
	private MediaProbeRequests probes;

	@Autowired
	private MediaCountersRepository counters;

	@Autowired
	private SecurityEventRecorder events;

	@Autowired
	private MediaClock clock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser leader;
	private final ExecutorService pool = Executors.newFixedThreadPool(2);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0978");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		saleA = survey.user("Worker Sale A", "SALE");
		saleB = survey.user("Worker Sale B", "SALE");
		leader = survey.user("Worker Leader", "SALE_LEADER");
	}

	@BeforeEach
	void onlyOwnJobs() {
		MediaIntakeFixture.open(jdbc);
		jdbc.update("update survey_media_jobs set state = 'DONE', lease_token = null where state = 'PENDING'");
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== SM-4 ====================

	@Test
	void oneFailing_neverChangesOthers() throws Exception {
		String checklistId = draft(saleA);
		String first = uploaded(saleA, checklistId, "first photo");
		String failing = uploaded(saleA, checklistId, "FAIL photo");
		String third = uploaded(saleA, checklistId, "third photo");
		MediaProcessingWorker worker = worker(source -> new String(source, StandardCharsets.UTF_8).startsWith("FAIL")
				? new ImageNormalizer.NormalizeResult.Failed("FULL_OVER_CAP") : normalized());

		runUntilIdle(worker);

		assertEquals("READY", status(first));
		assertEquals("FAILED", status(failing));
		assertEquals("READY", status(third));
		assertEquals(6, jdbc.queryForObject("select count(*) from survey_media_variants where media_id in (?, ?)",
				Integer.class, first, third));
		assertEquals(0, variantRows(failing));
		assertNull(jdbc.queryForObject("select intake_paused_since from survey_media_health where id = 1",
				Timestamp.class), "one job's outcome never pauses intake");
	}

	// ==================== SM-18 ====================

	@Test
	void lostLeaseAfterWritingVariants_cannotCommit_objectsDeleted_retryWritesNewKeys() throws Exception {
		String checklistId = draft(saleA);
		String id = uploaded(saleA, checklistId, "fenced photo");
		boolean[] stolen = {false};
		MediaProcessingWorker worker = worker(source -> {
			if (!stolen[0]) {
				stolen[0] = true;
				// another worker claimed the job after this one's lease ran out
				jdbc.update("update survey_media_jobs set lease_token = '01JOTHERWORKERLEASE0000001'"
						+ " where media_id = ? and kind = 'PROCESS'", id);
			}
			return normalized();
		});

		assertTrue(worker.runOnce());

		assertEquals("PROCESSING", status(id), "a fenced-out run commits nothing");
		assertEquals(0, variantRows(id));
		assertEquals(0, objects(id, "media/" + id + "/1/"), "its own variants are deleted");
		jdbc.update("update survey_media_jobs set lease_token = null, due_at = ? where media_id = ?",
				Timestamp.from(Instant.now().minusSeconds(1)), id);

		assertTrue(worker.runOnce());

		assertEquals("READY", status(id));
		assertEquals(3, jdbc.queryForObject("select count(*) from survey_media_variants where media_id = ? and"
				+ " storage_key like ?", Integer.class, id, "media/" + id + "/2/%"), "the retry writes new keys");
	}

	// ==================== SM-32 ====================

	@Test
	void uploaderWith50SlowFiles_otherUploaderDelayedByAtMostOneJob() throws Exception {
		String checklistA = draft(saleA);
		String checklistB = draft(saleB);
		Instant earlier = Instant.now().minus(Duration.ofMinutes(5));
		List<String> slow = processingRows(checklistA, saleA, 50, earlier, "slow");
		String other = processingRows(checklistB, saleB, 1, Instant.now().minusSeconds(10), "quick").get(0);
		List<String> order = Collections.synchronizedList(new ArrayList<>());
		MediaProcessingWorker worker = worker(source -> {
			String text = new String(source, StandardCharsets.UTF_8);
			order.add(text);
			if (text.startsWith("slow")) {
				pause(30);
			}
			return normalized();
		});

		for (int i = 0; i < 3; i++) {
			worker.runOnce();
		}

		assertTrue(order.subList(0, 2).stream().anyMatch(text -> text.startsWith("quick")),
				"the other uploader waits at most one job: " + order);
		assertEquals("READY", status(other));
		assertTrue(slow.stream().filter(id -> "READY".equals(status(id))).count() >= 1);
	}

	@Test
	void twentyExpensiveOutcomes_nextCreateAndPut429Expensive_othersUnaffected() throws Exception {
		TestUser capped = survey.user("Worker Capped Sale", "SALE");
		String checklistA = draft(capped);
		String awaiting = media.created(capped, checklistA, "later photo".getBytes(StandardCharsets.UTF_8));
		String hits = uploaded(capped, checklistA, "limit photo");
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> counters.add(capped.userId(),
				ReadBudgetArithmetic.hourStart(clock.now()), MediaCounterKind.EXPENSIVE, 19));
		MediaProcessingWorker worker = worker(source -> new ImageNormalizer.NormalizeResult.LimitHit());

		worker.runOnce();

		assertEquals("PROCESSING", status(hits));
		Result create = media.create(capped, checklistA, MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"after the cap")));
		assertEquals(429, create.status(), create.rawBody());
		assertEquals("expensive", create.body().path("errors").path(0).path("field").asString());
		Result put = media.put(capped, awaiting, "later photo".getBytes(StandardCharsets.UTF_8));
		assertEquals(429, put.status(), put.rawBody());
		assertEquals("expensive", put.body().path("errors").path(0).path("field").asString());
		String checklistB = draft(saleB);
		assertEquals(201, media.create(saleB, checklistB, MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"other uploader"))).status());
	}

	// ==================== processor isolation ====================

	@Test
	void childCrashOrOom_failsOnlyItsRun_parallelRunCompletes() throws Exception {
		String checklistId = draft(saleA);
		String one = uploaded(saleA, checklistId, "parallel one");
		String two = uploaded(saleA, checklistId, "parallel two");
		try (FakeProcessor crashing = new FakeProcessor().script(FakeProcessor.Script.LIMIT_HIT);
				FakeProcessor valid = new FakeProcessor()) {
			MediaProcessingWorker crashingWorker = worker(new IsolatedProcessorClient("127.0.0.1", crashing.port(),
					"test-fake", Duration.ofSeconds(5), Duration.ofSeconds(10)));
			MediaProcessingWorker validWorker = worker(new IsolatedProcessorClient("127.0.0.1", valid.port(),
					"test-fake", Duration.ofSeconds(5), Duration.ofSeconds(10)));
			// each worker claims any due job: only this test's two may be due when they start (a job of an earlier
			// test that became due meanwhile would take a worker, SV-069 CI)
			jdbc.update("update survey_media_jobs set state = 'DONE', lease_token = null where state = 'PENDING'"
					+ " and media_id not in (?, ?)", one, two);
			assertEquals(2, jdbc.queryForObject("select count(*) from survey_media_jobs where state = 'PENDING'"
					+ " and kind = 'PROCESS'", Integer.class), "only this test's jobs are due");
			CountDownLatch start = new CountDownLatch(1);
			Future<Boolean> a = pool.submit(() -> {
				start.await();
				return crashingWorker.runOnce();
			});
			Future<Boolean> b = pool.submit(() -> {
				start.await();
				return validWorker.runOnce();
			});
			start.countDown();
			assertTrue(a.get(60, TimeUnit.SECONDS));
			assertTrue(b.get(60, TimeUnit.SECONDS));
		}

		List<String> statuses = new ArrayList<>(List.of(status(one), status(two)));
		Collections.sort(statuses);
		String jobs = jdbc.queryForList("select media_id, kind, state, attempts, limit_hits, last_error_code from"
				+ " survey_media_jobs where kind = 'PROCESS' and (state = 'PENDING' or media_id in (?, ?))", one, two)
				.toString();
		assertEquals(List.of("PROCESSING", "READY"), statuses, "the child's limit hit fails only its own run: " + jobs);
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media_jobs where media_id in (?, ?)"
				+ " and limit_hits = 1 and state = 'PENDING'", Integer.class, one, two), one + " " + two + " " + jobs);
	}

	// ==================== A-17, SM-3 ====================

	@Test
	void sourceDeletedWithinOneHourOfReceipt() throws Exception {
		String checklistId = draft(saleA);
		String id = uploaded(saleA, checklistId, "source deletion");

		runUntilIdle(worker(source -> normalized()));

		Map<String, Object> row = jdbc.queryForMap("select content_received_at, source_deleted_at,"
				+ " source_storage_key from survey_media where id = ?", id);
		Instant received = ((Timestamp) row.get("content_received_at")).toInstant();
		Instant deleted = ((Timestamp) row.get("source_deleted_at")).toInstant();
		assertTrue(deleted.isBefore(received.plus(Duration.ofHours(1))));
		assertEquals(0, objects(id, (String) row.get("source_storage_key")));
	}

	@Test
	void streamOfLimitHittingAndCrashingFiles_neverPausesIntakeWhileProbesPass() throws Exception {
		String checklistId = draft(saleB);
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			ids.add(uploaded(saleB, checklistId, "troublesome " + i));
		}
		MediaProcessingWorker worker = worker(source -> new ImageNormalizer.NormalizeResult.LimitHit());

		for (int round = 0; round < 2; round++) {
			runUntilIdle(worker);
			jdbc.update("update survey_media_jobs set due_at = ? where state = 'PENDING'",
					Timestamp.from(Instant.now().minusSeconds(1)));
		}

		for (String id : ids) {
			assertEquals("REJECTED", status(id));
			assertEquals("UNREADABLE", jdbc.queryForObject("select status_reason from survey_media where id = ?",
					String.class, id));
		}
		assertNull(jdbc.queryForObject("select intake_paused_since from survey_media_health where id = 1",
				Timestamp.class), "only health probes pause intake (spec A-18)");
		assertEquals(201, media.create(saleB, checklistId, MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"still open"))).status());
	}

	// ==================== helpers ====================

	private String draft(TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return checklists.started(sale, visit.appointmentId());
	}

	/** Created and uploaded through the API: PROCESSING with its source in quarantine and a due job. */
	private String uploaded(TestUser sale, String checklistId, String text) throws Exception {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String id = media.created(sale, checklistId, bytes);
		Result put = media.put(sale, id, bytes);
		assertEquals(202, put.status(), put.rawBody());
		return id;
	}

	/** PROCESSING rows written directly, each source in quarantine and a job due from {@code dueAt}. */
	private List<String> processingRows(String checklistId, TestUser uploader, int count, Instant dueAt,
			String seed) {
		List<String> ids = media.insertPhotos(checklistId, uploader.userId(), Row.PROCESSING, count, dueAt, 100L,
				seed);
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			byte[] source = (seed + "#" + (i + 1)).getBytes(StandardCharsets.UTF_8);
			store.putCreateOnly(MediaObjectStore.Bucket.QUARANTINE, "quarantine/" + id + "/1",
					new ByteArrayInputStream(source), source.length);
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> jobs.upsertProcess(
					SurveyMediaId.of(id), dueAt, dueAt));
		}
		return ids;
	}

	private MediaProcessingWorker worker(Function<byte[], ImageNormalizer.NormalizeResult> answer) {
		return worker(new ImageNormalizer() {
			@Override
			public Optional<SourceFormat> detect(InputStream source, long length) {
				return Optional.of(SourceFormat.JPEG);
			}

			@Override
			public NormalizeResult normalize(InputStream source, long length) {
				try {
					return answer.apply(source.readAllBytes());
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}

			@Override
			public ProbeResult probe() {
				return ProbeResult.PASSED;
			}

			@Override
			public String processorVersion() {
				return "test-9";
			}
		});
	}

	private MediaProcessingWorker worker(ImageNormalizer normalizer) {
		MalwareScanner clean = new MalwareScanner() {
			@Override
			public ScanVerdict scan(InputStream content) {
				return ScanVerdict.CLEAN;
			}

			@Override
			public ProbeResult probe(Instant now) {
				return ProbeResult.PASSED;
			}
		};
		return new MediaProcessingWorker(jobs, mediaRepository, sets, variants, checklistRepository, store, clean,
				normalizer, health, probes, new MediaWorkCounters(counters, transactionManager, clock), events, clock,
				ulidGenerator, transactionManager);
	}

	private static ImageNormalizer.NormalizeResult normalized() {
		return new ImageNormalizer.NormalizeResult.Normalized(4000, 3000, List.of(
				new ImageNormalizer.NormalizedVariant(VariantKind.FULL, 4000, 3000, new byte[] {1, 2, 3}),
				new ImageNormalizer.NormalizedVariant(VariantKind.PREVIEW, 1600, 1200, new byte[] {4, 5}),
				new ImageNormalizer.NormalizedVariant(VariantKind.THUMBNAIL, 400, 300, new byte[] {6})));
	}

	private static void runUntilIdle(MediaProcessingWorker worker) {
		for (int i = 0; i < 100 && worker.runOnce(); i++) {
			// one job per run
		}
	}

	private String status(String mediaId) {
		return jdbc.queryForObject("select status from survey_media where id = ?", String.class, mediaId);
	}

	private int variantRows(String mediaId) {
		return jdbc.queryForObject("select count(*) from survey_media_variants where media_id = ?", Integer.class,
				mediaId);
	}

	/** Objects of the bucket under the key or prefix in the local store. */
	private int objects(String mediaId, String keyOrPrefix) {
		MediaObjectStore.Bucket bucket = keyOrPrefix.startsWith("media/") ? MediaObjectStore.Bucket.MEDIA
				: MediaObjectStore.Bucket.QUARANTINE;
		return store.list(bucket, keyOrPrefix, null, 100).objects().size();
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
