package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;

/**
 * The DELETE_CONTENT job on PostgreSQL (survey-media spec 4.7, A-17, A-23; plan Task A19): a photo held at the claim
 * keeps its content, and a listing that still shows an object retries the job. Needs Docker.
 */
class MediaDeletionJobPostgresTest extends MediaApiTestBase {

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
	private MediaContentDeletionJob deletion;

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
		survey = new SurveyApiTestSupport(api, jdbc, "0986");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Deletion Sale", "SALE");
		leader = survey.user("Deletion Leader", "SALE_LEADER");
		admin = survey.user("Deletion Admin", "ADMIN");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void reset() {
		store.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	/**
	 * spec A-23: a deletion is never requested while the photo is held, and the database refuses a hold on a photo
	 * whose deletion is requested (chk_survey_media_delete_request), so the job's re-check under the locks never
	 * finds one held; that re-check itself is MediaContentDeletionJobTest.heldAtClaim_deletesNothing.
	 */
	@Test
	void heldAtClaim_deletesNothing() throws Exception {
		String id = removedReady(51);

		assertThrows(DataIntegrityViolationException.class, () -> media.held(id, admin.userId(),
				"SECURITY_INCIDENT"));

		assertEquals(3, objects(id));
		assertNull(media.row(id).get("held_at"));
	}

	@Test
	void listingNotEmpty_retries() throws Exception {
		String id = removedReady(61);
		jdbc.update("update survey_media_jobs set due_at = ? where media_id = ?",
				Timestamp.from(Instant.now().minusSeconds(1)), id);
		store.skipDeletes.set(true);

		long until = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < until && !"LISTING_NOT_EMPTY".equals(media.jobs(id).get(0).get("last_error_code"))) {
			deletion.runOnce();
			Thread.sleep(100);
		}

		Map<String, Object> job = jdbc.queryForMap("select state, due_at, last_error_code, lease_token"
				+ " from survey_media_jobs where media_id = ?", id);
		assertEquals("PENDING", job.get("state"));
		assertEquals("LISTING_NOT_EMPTY", job.get("last_error_code"));
		assertNull(job.get("lease_token"));
		assertTrue(((Timestamp) job.get("due_at")).toInstant().isAfter(Instant.now()), "retried later");
		assertNull(media.row(id).get("content_deleted_at"));
		assertEquals(3, objects(id));

		store.skipDeletes.set(false);
		jdbc.update("update survey_media_jobs set due_at = ? where media_id = ?",
				Timestamp.from(Instant.now().minusSeconds(1)), id);
		until = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < until && media.row(id).get("content_deleted_at") == null) {
			deletion.runOnce();
			Thread.sleep(100);
		}
		assertEquals(0, objects(id));
		assertEquals("DONE", media.jobs(id).get(0).get("state"));
	}

	/** checklist submission spec I-5, D3-3: a photo a submission references keeps its content, whatever the job says. */
	@Test
	void deletionJob_referencedPhoto_neverDeleted() throws Exception {
		String id = removedReady(71);
		media.evidenceRow(media.row(id).get("checklist_id").toString(), id);
		jdbc.update("update survey_media_jobs set due_at = ? where media_id = ?",
				Timestamp.from(Instant.now().minusSeconds(1)), id);

		long until = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < until && "PENDING".equals(media.jobs(id).get(0).get("state"))) {
			deletion.runOnce();
			Thread.sleep(100);
		}

		assertEquals("CANCELLED", media.jobs(id).get(0).get("state"));
		assertEquals("REFERENCED", media.jobs(id).get(0).get("last_error_code"));
		assertNull(media.row(id).get("content_deleted_at"));
		assertEquals(3, objects(id));
	}

	// ==================== helpers ====================

	/** A READY photo with its three variants, removed by its holder: the deletion requested, the job due later. */
	private String removedReady(int seed) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String checklistId = checklists.started(sale, visit.appointmentId());
		String id = media.readyWithVariants(store, checklistId, sale.userId(), MediaApiTestSupport.bytes(3000, seed),
				MediaApiTestSupport.bytes(2000, seed + 1), MediaApiTestSupport.bytes(1000, seed + 2));
		assertEquals(200, media.remove(sale, id, null).status());
		return id;
	}

	private int objects(String id) {
		return store.real().list(MediaObjectStore.Bucket.MEDIA, "media/" + id + "/", null, 10).objects().size();
	}
}
