package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.service.media.MediaProcessingWorker;
import vn.sunvibe.survey.core.application.service.media.MediaSweeper;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaWorkCounters;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;

/**
 * Survey photo acceptance across tasks (survey-media spec 5.2, 5.5; plan Task A24): READY after a reassignment and the
 * old Sale's reads (SM-7), the uploads-off matrix (SM-23), the body rules (SM-24), the work that writes no History
 * (SM-16), and the verification queries V1..V14 with no deadlock across the class (SM-35). SM-18 is
 * MediaWorkerPostgresTest.lostLeaseAfterWritingVariants_cannotCommit_objectsDeleted_retryWritesNewKeys; SM-1 and SM-2
 * through the processing container are the tagged phone-corpus run. Needs Docker.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MediaAcceptancePostgresTest extends MediaApiTestBase {

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
	private MediaSweeper sweeper;

	@Autowired
	private MediaContentDeletionJob deletion;

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
	private TestUser sale;
	private TestUser saleB;
	private TestUser leader;
	private TestUser admin;
	private final List<String> ownChecklists = new ArrayList<>();
	private long deadlocksBefore;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0995");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Acceptance Sale", "SALE");
		saleB = survey.user("Acceptance Sale B", "SALE");
		leader = survey.user("Acceptance Leader", "SALE_LEADER");
		admin = survey.user("Acceptance Admin", "ADMIN");
		deadlocksBefore = deadlocks();
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void reset() {
		switches.reset();
		store.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== SM-7 ====================

	@Test
	@Order(1)
	void reassignmentAfterCommit_photoEndsReadyInSameChecklistWithOriginalUploader() throws Exception {
		Draft draft = draft();
		String id = uploaded(draft, "reassigned photo");
		checklists.reassign(leader, draft.visit().leadId(), saleB);

		process(id);

		Map<String, Object> row = media.row(id);
		assertEquals("READY", row.get("status"));
		assertEquals(draft.id(), row.get("checklist_id"));
		assertEquals(sale.userId(), row.get("uploaded_by_user_id"));
	}

	@Test
	@Order(2)
	void afterReassignment_oldSaleListThumbnailPreviewFull404AtOnceWithEvent_newAssigneeReads() throws Exception {
		Draft draft = draft();
		String id = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 11),
				MediaApiTestSupport.bytes(2000, 12), MediaApiTestSupport.bytes(1000, 13));
		assertEquals(200, media.read(sale, id, "thumbnail").status());
		checklists.reassign(leader, draft.visit().leadId(), saleB);

		assertEquals(404, media.list(sale, draft.id(), null).status());
		for (String variant : List.of("thumbnail", "preview", "full")) {
			assertEquals(404, media.read(sale, id, variant).status(), variant);
		}
		assertTrue(media.mediaEvents(draft.visit().leadId()).size() >= 1, "a refusal event");
		assertEquals(200, media.list(saleB, draft.id(), null).status());
		assertEquals(200, media.read(saleB, id, "preview").status());
	}

	// ==================== SM-23 ====================

	@Test
	@Order(3)
	void uploadsOff_withPhotos_onlyCreateAndContent409_everythingElseWorks_withoutPhotosNothingWritten()
			throws Exception {
		Draft draft = parts();
		String awaiting = media.created(sale, draft.id(), MediaApiTestSupport.bytes(1500, 21));
		String ready = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 22),
				MediaApiTestSupport.bytes(2000, 23), MediaApiTestSupport.bytes(1000, 24));
		String onSpace2 = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 25),
				MediaApiTestSupport.bytes(2000, 26), MediaApiTestSupport.bytes(1000, 27));
		jdbc.update("update survey_media set space_id = ?, space_code_at_upload = 'P102', category = 'KITCHEN' where"
				+ " id = ?", draft.space2(), onSpace2);
		String toRemove = media.insertPhoto(draft.id(), sale.userId(), Row.FAILED, Instant.now().minus(Duration
				.ofMinutes(5)), sha());
		String expired = media.insertPhoto(draft.id(), sale.userId(), Row.UPLOAD_EXPIRED, Instant.now().minus(Duration
				.ofHours(26)), sha());
		String processing = uploaded(draft, "processed while uploads are off");
		switches.uploads(false);

		Result create = media.create(sale, draft.id(), MediaApiTestSupport.exterior(sha()));
		assertEquals(409, create.status(), create.rawBody());
		assertEquals("MEDIA_UPLOAD_DISABLED", create.body().path("code").asString());
		Result content = media.put(sale, awaiting, MediaApiTestSupport.bytes(1500, 21));
		assertEquals(409, content.status(), content.rawBody());

		JsonNode list = media.list(sale, draft.id(), null).body();
		assertFalse(list.path("uploadEnabled").asBoolean());
		assertEquals(200, media.read(sale, ready, "preview").status());
		assertEquals(200, media.read(sale, ready, "full?download=true").status());
		assertEquals(200, media.remove(sale, toRemove, null).status());
		checklists.saved(sale, draft.id(), "identification", c -> ((ArrayNode) c.path("spaces")).remove(1));
		assertNotNull(media.row(onSpace2).get("removed_at"), "the G1 cascade");
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		assertTrue(read.path("media").path("available").asBoolean());
		assertFalse(read.path("media").path("uploadEnabled").asBoolean());
		process(processing);
		assertEquals("READY", media.row(processing).get("status"), "processing goes on");
		sweeper.sweep();
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where id = ?", Integer.class, expired),
				"clean-up goes on");

		Draft without = draft();
		Result none = media.create(sale, without.id(), MediaApiTestSupport.exterior(sha()));
		assertEquals(409, none.status());
		assertEquals(-1, media.setVersion(without.id()), "no set row");
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where checklist_id = ?", Integer.class,
				without.id()));
	}

	// ==================== SM-24 ====================

	@Test
	@Order(4)
	void bodies_unknownOrMissingKeys400_over16KiB413_phoneRule() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.FAILED, Instant.now().minus(Duration.ofMinutes(5)),
				sha());
		String huge = "x".repeat(17 * 1024);
		String base = MediaApiTestSupport.MEDIA + "/" + id;

		assertEquals(400, checklists.api().post(sale, base + "/removal", Map.of("correction", Map.of(),
				"extra", 1)).status(), "unknown key");
		assertEquals(400, checklists.api().post(sale, base + "/removal", Map.of()).status(), "missing key");
		assertEquals(413, checklists.api().post(sale, base + "/removal", Map.of("x", huge)).status());
		assertEquals(400, checklists.api().post(admin, base + "/hold", Map.of("reason", "SECURITY_INCIDENT",
				"extra", 1)).status());
		assertEquals(400, checklists.api().post(admin, base + "/hold", Map.of()).status());
		assertEquals(413, checklists.api().post(admin, base + "/hold", Map.of("x", huge)).status());
		assertEquals(400, checklists.api().post(admin, base + "/hold-release", Map.of("extra", 1)).status());
		assertEquals(413, checklists.api().post(admin, base + "/hold-release", Map.of("x", huge)).status());
		Result caption = media.create(sale, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", "Gọi 0905123456", 1000L, sha()));
		assertEquals(400, caption.status(), caption.rawBody());
		assertEquals("caption", caption.body().path("errors").path(0).path("field").asString());
		assertEquals(null, media.row(id).get("removed_at"));
	}

	// ==================== SM-16, negative half ====================

	@Test
	@Order(5)
	void createOutcomesExpiryCleanupAndDeletion_writeNoHistory() throws Exception {
		Draft draft = draft();
		long leadVersion = survey.version(draft.visit().leadId());
		int activities = activities(draft);

		String created = media.created(sale, draft.id(), MediaApiTestSupport.bytes(1200, 31));
		String committed = uploaded(draft, "an outcome without History");
		int afterCommit = activities(draft);
		process(committed);
		String failing = uploaded(draft, "a source that went missing");
		jdbc.update("delete from survey_media_jobs where media_id = ? and kind = 'PROCESS'", failing);
		// jobs are due by the application clock; the database's now() can run ahead of it (Docker VM clock skew)
		Timestamp due = Timestamp.from(Instant.now().minusSeconds(1));
		jdbc.update("insert into survey_media_jobs (media_id, kind, state, attempts, limit_hits, due_at, created_at,"
				+ " updated_at) values (?, 'PROCESS', 'PENDING', 0, 0, ?, ?, ?)", failing, due, due, due);
		store.real().delete(MediaObjectStore.Bucket.QUARANTINE, (String) media.row(failing).get("source_storage_key"));
		process(failing);
		jdbc.update("update survey_media set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration
				.ofMinutes(61))), created);
		sweeper.sweep();
		jdbc.update("update survey_media set finished_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration
				.ofHours(25))), created);
		sweeper.sweep();
		String removedReady = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000,
				32), MediaApiTestSupport.bytes(2000, 33), MediaApiTestSupport.bytes(1000, 34));
		jdbc.update("update survey_media set removed_at = now(), removed_by_user_id = ?, removal_kind = 'HOLDER',"
				+ " content_delete_requested_at = now() where id = ?", sale.userId(), removedReady);
		jdbc.update("insert into survey_media_jobs (media_id, kind, state, attempts, limit_hits, due_at, created_at,"
				+ " updated_at) values (?, 'DELETE_CONTENT', 'PENDING', 0, 0, ?, ?, ?)", removedReady, due, due, due);
		for (int i = 0; i < 20 && media.row(removedReady).get("content_deleted_at") == null; i++) {
			deletion.runOnce();
		}

		assertEquals("READY", media.row(committed).get("status"));
		assertEquals("FAILED", media.row(failing).get("status"));
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where id = ?", Integer.class, created));
		assertNotNull(media.row(removedReady).get("content_deleted_at"));
		assertEquals(afterCommit + 1, activities(draft), "only the two content commits wrote History");
		assertEquals(activities + 2, activities(draft));
		assertEquals(leadVersion, survey.version(draft.visit().leadId()), "the lead version stays");
	}

	// ==================== SM-35 ====================

	@Test
	@Order(99)
	void verificationQueries_returnNoRows_noDeadlockAcrossTheClass() throws Exception {
		sweeper.sweep();

		assertEquals(List.of(), MediaVerificationQueries.violations(jdbc, ownChecklists));
		Thread.sleep(1500);
		assertEquals(deadlocksBefore, deadlocks(), "pg_stat_database.deadlocks across the class");
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id, String space2) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		ownChecklists.add(id);
		return new Draft(visit, id, null);
	}

	private Draft parts() throws Exception {
		Draft draft = draft();
		String space2 = checklists.newId();
		checklists.saved(sale, draft.id(), "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(checklists.newId(), "P101"), checklists.space(space2, "P102"))));
		return new Draft(draft.visit(), draft.id(), space2);
	}

	private String uploaded(Draft draft, String text) throws Exception {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String id = media.created(sale, draft.id(), bytes);
		Result put = media.put(sale, id, bytes);
		assertEquals(202, put.status(), put.rawBody());
		return id;
	}

	/** Runs a worker with a fake processor on this photo's job only (other pending jobs are not this class's). */
	private void process(String mediaId) {
		jdbc.update("update survey_media_jobs set state = 'CANCELLED', lease_token = null where state = 'PENDING' and"
				+ " kind = 'PROCESS' and media_id <> ?", mediaId);
		jdbc.update("update survey_media_jobs set due_at = ? where media_id = ? and kind = 'PROCESS'",
				Timestamp.from(Instant.now().minusSeconds(1)), mediaId);
		MediaProcessingWorker worker = worker();
		for (int i = 0; i < 5 && "PROCESSING".equals(media.row(mediaId).get("status")); i++) {
			worker.runOnce();
		}
	}

	private MediaProcessingWorker worker() {
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
		ImageNormalizer normalizer = new ImageNormalizer() {
			@Override
			public Optional<SourceFormat> detect(InputStream source, long length) {
				return Optional.of(SourceFormat.JPEG);
			}

			@Override
			public NormalizeResult normalize(InputStream source, long length) {
				return new NormalizeResult.Normalized(4000, 3000, List.of(
						new NormalizedVariant(VariantKind.FULL, 4000, 3000, new byte[] {1, 2, 3}),
						new NormalizedVariant(VariantKind.PREVIEW, 1600, 1200, new byte[] {4, 5}),
						new NormalizedVariant(VariantKind.THUMBNAIL, 400, 300, new byte[] {6})));
			}

			@Override
			public ProbeResult probe() {
				return ProbeResult.PASSED;
			}

			@Override
			public String processorVersion() {
				return "test-9";
			}
		};
		return new MediaProcessingWorker(jobs, mediaRepository, sets, variants, checklistRepository, store, clean,
				normalizer, health, probes, new MediaWorkCounters(counters, transactionManager, clock), events, clock,
				ulidGenerator, transactionManager);
	}

	private int activities(Draft draft) {
		return jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?", Integer.class,
				draft.visit().leadId());
	}

	private long deadlocks() {
		return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()",
				Long.class);
	}

	private static String sha() {
		return MediaApiTestSupport.sha(MediaApiTestSupport.newKey());
	}
}
