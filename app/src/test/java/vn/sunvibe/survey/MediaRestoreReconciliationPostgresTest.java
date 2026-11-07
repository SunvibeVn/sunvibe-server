package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.github.f4b6a3.ulid.UlidCreator;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.application.service.media.MediaRestoreReconciliation;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;

/**
 * The restore reconciliation (survey-media spec 5.1, 4.4 step 4; plan Task A22): after a database restore a READY
 * photo whose variants are gone from the store is marked CONTENT_MISSING (not servable); nothing is deleted; it runs
 * only when the operator sets the property for one start. Needs Docker.
 */
class MediaRestoreReconciliationPostgresTest extends MediaApiTestBase {

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
	private MediaRestoreReconciliation reconciliation;

	@Autowired
	private vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository mediaRepository;

	@Autowired
	private vn.sunvibe.survey.core.application.shared.media.MediaSetLocks setLocks;

	@Autowired
	private vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository variants;

	@Autowired
	private vn.sunvibe.survey.core.application.shared.media.MediaClock clock;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transactionManager;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0994");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Restore Sale", "SALE");
		leader = survey.user("Restore Leader", "SALE_LEADER");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void missingVariants_markContentMissing_listShowsContentMissing() throws Exception {
		String checklistId = draft();
		String id = ready(checklistId, 61);
		long before = media.setVersion(checklistId);
		Files.delete(objectPath(Bucket.MEDIA, "media/" + id + "/1/PREVIEW"));

		reconciliation.run();

		assertNotNull(media.row(id).get("content_missing_at"));
		assertTrue(media.setVersion(checklistId) > before);
		JsonNode item = media.list(sale, checklistId, null).body().path("items").path(0);
		assertEquals("CONTENT_MISSING", item.path("unavailableReason").asString());
		assertEquals(409, media.read(sale, id, "full").status());
	}

	@Test
	void present_unchanged() throws Exception {
		String checklistId = draft();
		String id = ready(checklistId, 71);
		long before = media.setVersion(checklistId);

		reconciliation.run();

		assertNull(media.row(id).get("content_missing_at"));
		assertEquals(before, media.setVersion(checklistId));
	}

	@Test
	void deletesNothing() throws Exception {
		String checklistId = draft();
		String id = ready(checklistId, 81);
		Files.delete(objectPath(Bucket.MEDIA, "media/" + id + "/1/FULL"));
		String orphan = "media/" + UlidCreator.getMonotonicUlid() + "/1/FULL";
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, orphan, Duration.ofDays(3));

		reconciliation.run();

		assertTrue(MediaApiTestSupport.objectExists(Bucket.MEDIA, orphan), "orphans go by the sweep");
		assertTrue(MediaApiTestSupport.objectExists(Bucket.MEDIA, "media/" + id + "/1/PREVIEW"));
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media where id = ?", Integer.class, id));
		assertEquals(3, jdbc.queryForObject("select count(*) from survey_media_variants where media_id = ?",
				Integer.class, id));
	}

	// ==================== guards against a wrong or empty bucket (migration review I-1) ====================

	@Test
	void storeProbeFails_marksNothing() throws Exception {
		String id = readyWithOneVariantGone(91);
		store.failProbe.set(true);
		try {
			assertEquals(0, reconciliation.run());
		} finally {
			store.reset();
		}

		assertNull(media.row(id).get("content_missing_at"));
	}

	@Test
	void emptyMediaBucket_marksNothing() throws Exception {
		String checklistId = draft();
		String id = ready(checklistId, 101);
		store.emptyMediaBucket.set(true);
		try {
			assertEquals(0, reconciliation.run());
		} finally {
			store.reset();
		}

		assertNull(media.row(id).get("content_missing_at"), "an empty bucket after a restore is a wrong bucket");
	}

	@Test
	void mostPhotosMissing_marksNothingUnlessForced() throws Exception {
		String checklistId = draft();
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 25; i++) {
			ids.add(ready(checklistId, 200 + 3 * i));
		}
		store.emptyPhotoPrefixes.set(true);
		try {
			assertEquals(0, reconciliation.run(), "more than half look missing: nothing is marked");
			for (String id : ids) {
				assertNull(media.row(id).get("content_missing_at"));
			}

			MediaRestoreReconciliation forced = new MediaRestoreReconciliation(mediaRepository, setLocks, variants, store,
					clock, transactionManager, false, true);
			assertTrue(forced.run() >= ids.size(), "the operator's override marks them");
		} finally {
			store.reset();
		}
		for (String id : ids) {
			assertNotNull(media.row(id).get("content_missing_at"));
		}
	}

	@Test
	void offByDefault() {
		assertFalse(reconciliation.enabledAtStartup(), "app.survey.media.reconcile-after-restore is false by default");
	}

	/** Spec 5.1, plan Task A22: the runbook names the duration per 10,000 photos measured here. */
	@Test
	void measured10000PhotosDuration_recorded() throws Exception {
		String checklistId = draft();
		jdbc.update("insert into survey_media_sets (checklist_id, set_version, last_media_no, updated_at) values"
				+ " (?, 0, 0, now()) on conflict (checklist_id) do nothing", checklistId);
		jdbc.update("insert into survey_media (id, checklist_id, upload_key, media_no, version, category,"
				+ " uploaded_by_user_id, created_at, declared_size_bytes, source_sha256, status, content_received_at,"
				+ " source_storage_key, source_deleted_at, source_format, ready_at, finished_at)"
				+ " select '01JRC' || lpad(g::text, 21, '0'), ?, 'RK' || lpad(g::text, 24, '0'), 100000 + g, 0, 'DETAIL', ?,"
				+ " now(), 1000, lpad(to_hex(g), 64, '0'), 'READY', now(), 'quarantine/x/' || g, now(), 'JPEG', now(),"
				+ " now() from generate_series(1, 10000) g", checklistId, sale.userId());
		jdbc.update("insert into survey_media_variants (media_id, variant, attempt, storage_key, sha256, size_bytes,"
				+ " width, height, processor_version, created_at) select '01JRC' || lpad(g::text, 21, '0'), v, 1,"
				+ " 'media/01JRC' || lpad(g::text, 21, '0') || '/1/' || v, lpad(to_hex(g), 64, '0'), 3, 400, 300, 'test-9',"
				+ " now() from generate_series(1, 10000) g, unnest(array['FULL', 'PREVIEW', 'THUMBNAIL']) v");
		Path base = objectPath(Bucket.MEDIA, "media");
		for (int g = 1; g <= 10_000; g++) {
			Path dir = base.resolve("01JRC" + String.format("%021d", g)).resolve("1");
			Files.createDirectories(dir);
			for (String variant : new String[] {"FULL", "PREVIEW", "THUMBNAIL"}) {
				Files.write(dir.resolve(variant), new byte[] {1, 2, 3});
			}
		}
		try {
			long started = System.nanoTime();
			reconciliation.run();
			Duration took = Duration.ofNanos(System.nanoTime() - started);

			System.out.println("RESTORE-RECONCILIATION 10000 photos took " + took.toMillis() + " ms (local store)");
			assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where id like '01JRC%' and"
					+ " content_missing_at is not null", Integer.class));
			assertTrue(took.compareTo(Duration.ofMinutes(5)) < 0, "took " + took);
		} finally {
			jdbc.update("delete from survey_media_variants where media_id like '01JRC%'");
			jdbc.update("delete from survey_media where id like '01JRC%'");
			for (int g = 1; g <= 10_000; g++) {
				Path dir = base.resolve("01JRC" + String.format("%021d", g));
				for (String variant : new String[] {"FULL", "PREVIEW", "THUMBNAIL"}) {
					Files.deleteIfExists(dir.resolve("1").resolve(variant));
				}
				Files.deleteIfExists(dir.resolve("1"));
				Files.deleteIfExists(dir);
			}
		}
	}

	// ==================== helpers ====================

	private String draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return checklists.started(sale, visit.appointmentId());
	}

	private String readyWithOneVariantGone(int seed) throws Exception {
		String id = ready(draft(), seed);
		Files.delete(objectPath(Bucket.MEDIA, "media/" + id + "/1/THUMBNAIL"));
		return id;
	}

	private String ready(String checklistId, int seed) {
		return media.readyWithVariants(store, checklistId, sale.userId(), MediaApiTestSupport.bytes(3000, seed),
				MediaApiTestSupport.bytes(2000, seed + 1), MediaApiTestSupport.bytes(1000, seed + 2));
	}

	private static Path objectPath(Bucket bucket, String key) {
		return Path.of(System.getProperty("java.io.tmpdir"), "sunvibe-media-api-test", "store",
				bucket == Bucket.QUARANTINE ? "quarantine-bucket" : "media-bucket").resolve(key);
	}
}
