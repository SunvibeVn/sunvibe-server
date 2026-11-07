package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.github.f4b6a3.ulid.UlidCreator;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.RawUpload;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.service.media.MediaProbeRunner;
import vn.sunvibe.survey.core.application.service.media.MediaSweeper;
import vn.sunvibe.survey.core.application.shared.media.IntakeGate;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaBodySlotDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository;

/**
 * Intake pauses and resumes (survey-media spec A-18, 4.4 step 4; SM-14; plan Task A21): every cause pauses create and
 * content on every instance through the shared health row, a sweep resumes only with the failure sequence it read
 * first, overdue deletions open one incident that the resuming sweep closes, and reads, removal and hold go on while
 * paused. A second instance is a second gate and a second sweeper over the same database. Needs Docker.
 */
class MediaIntakePausePostgresTest extends MediaApiTestBase {

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

	@Autowired
	private MediaTestProbes probeAnswers;

	@Autowired
	private MediaSweeper sweeper;

	@Autowired
	private MediaProbeRunner probes;

	@Autowired
	private IntakeHealthRepository health;

	@Autowired
	private MediaClock clock;

	@Autowired
	private SurveyMediaDomainRepository mediaRepository;

	@Autowired
	private SurveyMediaSetDomainRepository sets;

	@Autowired
	private SurveyMediaJobDomainRepository jobs;

	@Autowired
	private SurveyMediaVariantDomainRepository variants;

	@Autowired
	private MediaCountersRepository counters;

	@Autowired
	private SurveyMediaBodySlotDomainRepository slots;

	@Autowired
	private MediaIncidentRecorder incidentRecorder;

	@Autowired
	private MediaSwitches mediaSwitches;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private vn.sunvibe.survey.core.domain.repository.SurveyMediaListingRunDomainRepository listingRuns;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0993");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Pause Sale", "SALE");
		leader = survey.user("Pause Leader", "SALE_LEADER");
		admin = survey.user("Pause Admin", "ADMIN");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
		freshHeartbeats();
	}

	/** Each test leaves no overdue item, due job or open incident behind for the next sweep. */
	@AfterEach
	void reset() {
		store.reset();
		switches.reset();
		probeAnswers.reset();
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("update survey_media set source_deleted_at = ? where source_storage_key is not null and"
				+ " source_deleted_at is null and status not in ('RECEIVING', 'PROCESSING')", now);
		jdbc.update("update survey_media set content_deleted_at = ? where content_delete_requested_at is not null"
				+ " and content_deleted_at is null", now);
		jdbc.update("update survey_media_jobs set state = 'CANCELLED', lease_token = null where state = 'PENDING'");
		jdbc.update("update survey_media_incidents set resolved_at = greatest(?, opened_at) where resolved_at is null",
				now);
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== one test per pause cause ====================

	@Test
	void store_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		store.failProbe.set(true);

		probes.runAll();
		assertOpen("the first incomplete store probe does not pause");
		probes.runAll();

		assertPausedEverywhere(pending, "STORE");
	}

	@Test
	void scanner_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		probeAnswers.scanner = ProbeResult.WRONG_ANSWER;

		probes.runAll();

		assertPausedEverywhere(pending, "SCANNER");
		assertNotNull(jdbc.queryForObject("select scan_blocked_since from survey_media_health where id = 1",
				Timestamp.class));
		probeAnswers.scanner = ProbeResult.PASSED;
		probes.runAll();
	}

	@Test
	void processor_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		probeAnswers.processor = ProbeResult.WRONG_ANSWER;

		probes.runAll();

		assertPausedEverywhere(pending, "PROCESSOR");
	}

	@Test
	void staleSweep_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		jdbc.update("update survey_media_health set last_sweep_ok_at = ? where id = 1",
				Timestamp.from(Instant.now().minus(Duration.ofMinutes(11))));

		assertPausedEverywhere(pending, null);
	}

	@Test
	void processingOff_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		switches.processing(false);

		sweeper.sweep();

		assertPausedEverywhere(pending, "PROCESSING_OFF");
	}

	@Test
	void workerHeartbeat_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		String processing = media.insertPhoto(pending.draft().id(), sale.userId(), Row.PROCESSING,
				Instant.now().minus(Duration.ofMinutes(5)), sha());
		jdbc.update("insert into survey_media_jobs (media_id, kind, state, attempts, limit_hits, due_at, created_at,"
				+ " updated_at) values (?, 'PROCESS', 'PENDING', 0, 0, ?, ?, ?)", processing, ts(-60), ts(-60), ts(-60));
		jdbc.update("update survey_media_health set last_worker_beat_at = ? where id = 1", ts(-11 * 60));

		sweeper.sweep();

		assertPausedEverywhere(pending, "WORKER_STALE");
	}

	@Test
	void deletionHeartbeat_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		String removed = media.insertPhoto(pending.draft().id(), sale.userId(), Row.REMOVED_READY,
				Instant.now().minus(Duration.ofMinutes(30)), sha());
		jdbc.update("insert into survey_media_jobs (media_id, kind, state, attempts, limit_hits, due_at, created_at,"
				+ " updated_at) values (?, 'DELETE_CONTENT', 'PENDING', 0, 0, ?, ?, ?)", removed, ts(-60), ts(-60),
				ts(-60));
		jdbc.update("update survey_media_health set last_deletion_beat_at = ? where id = 1", ts(-11 * 60));

		sweeper.sweep();

		assertPausedEverywhere(pending, "DELETION_STALE");
	}

	@Test
	void overdueSource_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		String id = media.insertPhoto(pending.draft().id(), sale.userId(), Row.READY,
				Instant.now().minus(Duration.ofMinutes(62)), sha());
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, (String) media.row(id).get("source_storage_key"),
				Duration.ofMinutes(61));
		store.failDeletes.set(true);

		sweeper.sweep();

		assertPausedEverywhere(pending, "DELETION_OVERDUE");
		assertEquals(List.of("SOURCE_OVERDUE"), openIncidents());
	}

	@Test
	void removedContentNotDeleted24HoursAfterRequest_pausesIntake() throws Exception {
		Pending pending = pending();
		String id = media.insertPhoto(pending.draft().id(), sale.userId(), Row.REMOVED_READY,
				Instant.now().minus(Duration.ofHours(26)), sha());
		jdbc.update("update survey_media set content_delete_requested_at = ? where id = ?", ts(-25 * 3600), id);

		sweeper.sweep();

		assertPausedEverywhere(pending, "DELETION_OVERDUE");
		assertEquals(List.of("CONTENT_DELETION_OVERDUE"), openIncidents());
	}

	@Test
	void overdueOrphan_pausesCreateAndContentOnTwoInstances() throws Exception {
		Pending pending = pending();
		String orphan = "media/" + UlidCreator.getMonotonicUlid() + "/1/FULL";
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, orphan, Duration.ofHours(25));

		sweeper.sweep();

		assertPausedEverywhere(pending, "DELETION_OVERDUE");
		assertEquals(List.of("ORPHAN_OVERDUE"), openIncidents());
		assertFalse(MediaApiTestSupport.objectExists(Bucket.MEDIA, orphan));
	}

	// ==================== the other rules ====================

	@Test
	void streamInFlight_refusedAtCommitObjectDeleted() throws Exception {
		Draft draft = draft();
		byte[] bytes = MediaApiTestSupport.bytes(5000, 91);
		String id = media.created(sale, draft.id(), bytes);
		try (RawUpload upload = media.openUpload(sale, id, bytes.length, MediaApiTestSupport.digest(bytes))) {
			upload.send(Arrays.copyOf(bytes, 2000));
			awaitStatus(id, "RECEIVING");
			probeAnswers.processor = ProbeResult.WRONG_ANSWER;
			probes.runAll();

			upload.send(Arrays.copyOfRange(bytes, 2000, bytes.length));
			Map<String, String> answer = upload.answer(20_000);
			assertEquals("503", answer.get(":status"), answer.toString());
		}
		assertNotEquals("PROCESSING", media.row(id).get("status"));
		assertEquals(0, store.real().list(Bucket.QUARANTINE, "quarantine/" + id + "/", null, 10).objects().size());
	}

	@Test
	void failureDuringSweep_notOverwrittenByItsResume_alsoEqualClocks() {
		MediaIntakeFixture.pause(jdbc, vn.sunvibe.survey.core.domain.model.media.PauseReason.STORE);
		store.failProbe.set(true);

		sweeper.sweep();

		assertNotNull(pausedSince(), "the failure in the sweep moved failure_seq: no resume");
		store.failProbe.set(false);
		sweeper.sweep();
		assertNull(pausedSince(), "the next clean sweep resumes");
	}

	@Test
	void freshDatabase_pausedUntilStartupSweep() throws Exception {
		Draft draft = draft();
		MediaIntakeFixture.reset(jdbc);
		assertEquals(503, create(draft).status());

		sweeper.sweep();

		assertEquals(201, create(draft).status());
	}

	@Test
	void source50MinutesOld_neverReady() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.PROCESSING, Instant.now().minus(Duration.ofMinutes(
				52)), sha());
		jdbc.update("insert into survey_media_jobs (media_id, kind, state, attempts, limit_hits, due_at, created_at,"
				+ " updated_at) values (?, 'PROCESS', 'PENDING', 1, 0, ?, ?, ?)", id, ts(600), ts(-3000), ts(-60));

		sweeper.sweep();

		assertEquals("FAILED", media.row(id).get("status"));
		assertEquals("PROCESSING_UNAVAILABLE", media.row(id).get("status_reason"));
		assertNotEquals("PENDING", media.jobs(id).get(0).get("state"));
	}

	@Test
	void overdueDeletion_oneIncidentFromTwoInstances_closedByResumingSweep() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.REMOVED_READY,
				Instant.now().minus(Duration.ofHours(26)), sha());
		jdbc.update("update survey_media set content_delete_requested_at = ? where id = ?", ts(-25 * 3600), id);
		MediaSweeper secondInstance = new MediaSweeper(mediaRepository, sets, jobs, variants, store, counters, slots,
				health, incidentRecorder, mediaSwitches, probes, listingRuns, clock, transactionManager);

		CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(sweeper::sweep);
		CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(secondInstance::sweep);
		first.join();
		second.join();

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media_incidents where resolved_at is null",
				Integer.class));
		assertNotNull(pausedSince());

		jdbc.update("update survey_media set content_deleted_at = ? where id = ?", ts(0), id);
		sweeper.sweep();

		assertNull(pausedSince());
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media_incidents where resolved_at is null",
				Integer.class));
	}

	@Test
	void readsRemovalHold_workWhilePaused() throws Exception {
		Draft draft = draft();
		String ready = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 93),
				MediaApiTestSupport.bytes(2000, 94), MediaApiTestSupport.bytes(1000, 95));
		String other = media.insertPhoto(draft.id(), sale.userId(), Row.READY, Instant.now().minus(Duration
				.ofMinutes(5)), sha());
		MediaIntakeFixture.pause(jdbc, vn.sunvibe.survey.core.domain.model.media.PauseReason.STORE);

		assertEquals(200, media.list(sale, draft.id(), null).status());
		assertEquals(200, media.read(sale, ready, "preview").status());
		assertEquals(200, checklists.api().post(admin, MediaApiTestSupport.MEDIA + "/" + ready + "/hold",
				Map.of("reason", "SECURITY_INCIDENT")).status());
		assertEquals(200, media.remove(sale, other, null).status());
	}

	@Test
	void heldPhotos_excludedFromDeletionOverdue() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.READY, Instant.now().minus(Duration.ofHours(30)),
				sha());
		media.held(id, admin.userId(), "SECURITY_INCIDENT");
		jdbc.update("update survey_media set removed_at = ?, removed_by_user_id = ?, removal_kind = 'HOLDER'"
				+ " where id = ?", ts(-26 * 3600), sale.userId(), id);

		sweeper.sweep();

		assertTrue(openIncidents().isEmpty(), openIncidents().toString());
		assertNull(pausedSince());
	}

	@Test
	void removedWhileAwaiting_expiresThenDeleted24HoursLater() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.AWAITING_CONTENT,
				Instant.now().minus(Duration.ofMinutes(61)), sha());
		assertEquals(200, media.remove(sale, id, null).status());

		sweeper.sweep();
		assertEquals("UPLOAD_EXPIRED", media.row(id).get("status"));

		jdbc.update("update survey_media set finished_at = ? where id = ?", ts(-25 * 3600), id);
		sweeper.sweep();

		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where id = ?", Integer.class, id));
	}

	@Test
	void processingOff_pausesIntake_sourcesStillDeletedWithinOneHour() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.PROCESSING, Instant.now().minus(Duration.ofMinutes(
				52)), sha());
		String source = (String) media.row(id).get("source_storage_key");
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, source, Duration.ofMinutes(51));
		switches.processing(false);

		sweeper.sweep();

		assertEquals("FAILED", media.row(id).get("status"));
		assertNotNull(media.row(id).get("source_deleted_at"));
		assertFalse(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, source));
		assertEquals("PROCESSING_OFF", jdbc.queryForObject("select pause_reason from survey_media_health where id = 1",
				String.class));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	/** A draft with one photo created while intake was open, waiting for its bytes. */
	private record Pending(Draft draft, String mediaId, byte[] bytes) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private Pending pending() throws Exception {
		Draft draft = draft();
		byte[] bytes = MediaApiTestSupport.bytes(1500, (int) (System.nanoTime() % 1000));
		return new Pending(draft, media.created(sale, draft.id(), bytes), bytes);
	}

	private Result create(Draft draft) throws Exception {
		return media.create(sale, draft.id(), MediaApiTestSupport.exterior(sha()));
	}

	/** Create and content answer 503 MEDIA_INTAKE_PAUSED here, and a second instance's gate refuses too. */
	private void assertPausedEverywhere(Pending pending, String reasonOrNull) throws Exception {
		Result created = create(pending.draft());
		assertEquals(503, created.status(), created.rawBody());
		assertEquals("MEDIA_INTAKE_PAUSED", created.body().path("code").asString());
		Result content = media.put(sale, pending.mediaId(), pending.bytes());
		assertEquals(503, content.status(), content.rawBody());
		assertThrows(MediaIntakePausedException.class, () -> new IntakeGate(health, clock).requireOpen(),
				"a second instance reads the same row");
		if (reasonOrNull != null) {
			assertEquals(reasonOrNull, jdbc.queryForObject("select pause_reason from survey_media_health where id = 1",
					String.class));
		}
	}

	private void assertOpen(String message) {
		assertNull(pausedSince(), message);
	}

	private Timestamp pausedSince() {
		return jdbc.queryForObject("select intake_paused_since from survey_media_health where id = 1",
				Timestamp.class);
	}

	private List<String> openIncidents() {
		return jdbc.queryForList("select kind from survey_media_incidents where resolved_at is null order by kind",
				String.class);
	}

	private void freshHeartbeats() {
		jdbc.update("update survey_media_health set last_worker_beat_at = ?, last_deletion_beat_at = ? where id = 1",
				ts(0), ts(0));
	}

	private static Timestamp ts(long secondsFromNow) {
		return Timestamp.from(Instant.now().plusSeconds(secondsFromNow));
	}

	private static String sha() {
		return MediaApiTestSupport.sha(MediaApiTestSupport.newKey());
	}

	private void awaitStatus(String id, String status) throws InterruptedException {
		long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!status.equals(media.row(id).get("status")) && System.nanoTime() < until) {
			Thread.sleep(50);
		}
		assertEquals(status, media.row(id).get("status"));
	}

	private static void assertNotEquals(Object unexpected, Object actual) {
		org.junit.jupiter.api.Assertions.assertNotEquals(unexpected, actual);
	}
}
