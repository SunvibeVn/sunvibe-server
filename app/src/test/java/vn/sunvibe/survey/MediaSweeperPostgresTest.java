package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.github.f4b6a3.ulid.UlidCreator;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.service.media.MediaSweeper;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;

/**
 * The sweep on PostgreSQL and the local store (survey-media spec 4.4 step 4, A-17, A-26; SM-13; plan Task A21):
 * expired records, sources, attempts and orphans go at their ages, probe keys and served photos stay, and the W16 rows
 * are never touched. Needs Docker.
 */
class MediaSweeperPostgresTest extends MediaApiTestBase {

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
	private MediaTestProbes probes;

	@Autowired
	private MediaSweeper sweeper;

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
		survey = new SurveyApiTestSupport(api, jdbc, "0992");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Sweep Sale", "SALE");
		leader = survey.user("Sweep Leader", "SALE_LEADER");
		admin = survey.user("Sweep Admin", "ADMIN");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void reset() {
		store.reset();
		switches.reset();
		probes.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void uploadExpired_deleted24HoursLaterInBatchesNoFkErrorSetVersionBumped() throws Exception {
		Draft draft = draft();
		media.insertPhotos(draft.id(), sale.userId(), Row.UPLOAD_EXPIRED, 501, Instant.now().minus(Duration.ofHours(26)),
				1000L, "sweep-expired");
		String recent = media.insertPhoto(draft.id(), sale.userId(), Row.UPLOAD_EXPIRED, Instant.now().minus(
				Duration.ofHours(1)), MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));
		long before = media.setVersion(draft.id());

		assertTrue(sweeper.sweep());

		assertEquals(List.of(recent), jdbc.queryForList("select id from survey_media where checklist_id = ?",
				String.class, draft.id()));
		assertTrue(media.setVersion(draft.id()) > before);
	}

	@Test
	void rejectedFailed_keepRecordNoObject() throws Exception {
		Draft draft = draft();
		for (Row row : List.of(Row.REJECTED, Row.FAILED)) {
			String id = photo(draft, row, Duration.ofMinutes(30));
			String source = (String) media.row(id).get("source_storage_key");
			MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, source, Duration.ofMinutes(30));

			sweeper.sweep();

			Map<String, Object> after = media.row(id);
			assertEquals(row.name(), after.get("status"));
			assertNotNull(after.get("source_deleted_at"), row.name());
			assertFalse(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, source), row.name());
		}
	}

	@Test
	void ready400DaysOnClosedLeadAndReadOnlyDraft_stillServed() throws Exception {
		Draft draft = draft();
		String id = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 51),
				MediaApiTestSupport.bytes(2000, 52), MediaApiTestSupport.bytes(1000, 53));
		Timestamp old = Timestamp.from(Instant.now().minus(Duration.ofDays(400)));
		jdbc.update("update survey_media set created_at = ?, content_received_at = ?, ready_at = ?, finished_at = ?,"
				+ " source_deleted_at = ? where id = ?", old, old, old, old, old, id);
		SurveyApiTestSupport.ok(survey.moveTo(leader, draft.visit().leadId(), "ON_HOLD"));

		sweeper.sweep();
		deletion.runOnce();

		assertEquals(200, media.read(admin, id, "full").status());
		assertEquals("READY", media.row(id).get("status"));
	}

	@Test
	void w16RowsUntouchedBySweepAndDeletion() throws Exception {
		Draft draft = draft();
		photo(draft, Row.AWAITING_CONTENT, Duration.ofHours(2));
		List<String> tables = List.of("survey_checklists", "survey_checklist_groups", "survey_checklist_saves",
				"survey_checklist_takeovers", "survey_appointments");
		Map<String, String> before = new java.util.LinkedHashMap<>();
		for (String table : tables) {
			before.put(table, digest(table));
		}

		sweeper.sweep();
		deletion.runOnce();

		for (String table : tables) {
			assertEquals(before.get(table), digest(table), table);
		}
	}

	@Test
	void staleReceivingAttempt_deleted() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.AWAITING_CONTENT, Duration.ofMinutes(30));
		media.receiving(id);
		jdbc.update("update survey_media set receive_started_at = ? where id = ?",
				Timestamp.from(Instant.now().minus(Duration.ofMinutes(20))), id);
		String attempt = (String) media.row(id).get("receive_key");
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, attempt, Duration.ofMinutes(20));

		sweeper.sweep();

		assertEquals("AWAITING_CONTENT", media.row(id).get("status"));
		assertFalse(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, attempt));
	}

	@Test
	void leftoverSource_deleted() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY, Duration.ofMinutes(10));
		String source = (String) media.row(id).get("source_storage_key");
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, source, Duration.ofMinutes(10));

		sweeper.sweep();

		assertNotNull(media.row(id).get("source_deleted_at"));
		assertFalse(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, source));
	}

	@Test
	void orphansDeleted_probeKeysNever() throws Exception {
		String quarantineOrphan = "quarantine/" + UlidCreator.getMonotonicUlid() + "/1";
		String quarantineYoung = "quarantine/" + UlidCreator.getMonotonicUlid() + "/1";
		String quarantineProbe = "quarantine/health/" + UlidCreator.getMonotonicUlid();
		String mediaOrphan = "media/" + UlidCreator.getMonotonicUlid() + "/1/FULL";
		String mediaYoung = "media/" + UlidCreator.getMonotonicUlid() + "/1/FULL";
		String mediaProbe = "health/" + UlidCreator.getMonotonicUlid();
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, quarantineOrphan, Duration.ofMinutes(56));
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, quarantineYoung, Duration.ofMinutes(50));
		MediaApiTestSupport.storedObject(store, Bucket.QUARANTINE, quarantineProbe, Duration.ofDays(3));
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, mediaOrphan, Duration.ofMinutes(61));
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, mediaYoung, Duration.ofMinutes(30));
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, mediaProbe, Duration.ofDays(3));

		sweeper.sweep();

		assertFalse(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, quarantineOrphan));
		assertTrue(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, quarantineYoung));
		assertTrue(MediaApiTestSupport.objectExists(Bucket.QUARANTINE, quarantineProbe));
		assertFalse(MediaApiTestSupport.objectExists(Bucket.MEDIA, mediaOrphan));
		assertTrue(MediaApiTestSupport.objectExists(Bucket.MEDIA, mediaYoung));
		assertTrue(MediaApiTestSupport.objectExists(Bucket.MEDIA, mediaProbe));
	}

	@Test
	void budgetRowsOver2Hours_deleted() {
		Instant hour = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
		for (int hoursAgo : List.of(3, 1)) {
			jdbc.update("insert into survey_media_read_budgets (user_id, hour_start, kind, read_count) values (?, ?,"
					+ " 'LIST', 1) on conflict do nothing", sale.userId(), Timestamp.from(hour.minus(Duration.ofHours(
					hoursAgo))));
		}

		sweeper.sweep();

		List<Timestamp> left = jdbc.queryForList("select hour_start from survey_media_read_budgets where user_id = ?",
				Timestamp.class, sale.userId());
		assertEquals(List.of(Timestamp.from(hour.minus(Duration.ofHours(1)))), left);
	}

	@Test
	void expiredSlots_deleted() {
		Instant now = Instant.now();
		jdbc.update("insert into survey_media_body_slots (user_id, instance_id, taken_at, expires_at) values"
				+ " (?, 'sweep-test', ?, ?), (?, 'sweep-test', ?, ?)", sale.userId(), Timestamp.from(now.minusSeconds(
				600)), Timestamp.from(now.minusSeconds(60)), sale.userId(), Timestamp.from(now), Timestamp.from(now
				.plusSeconds(600)));

		sweeper.sweep();

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media_body_slots where instance_id ="
				+ " 'sweep-test'", Integer.class));
		jdbc.update("delete from survey_media_body_slots where instance_id = 'sweep-test'");
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private String photo(Draft draft, Row row, Duration age) {
		return media.insertPhoto(draft.id(), sale.userId(), row, Instant.now().minus(age),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));
	}

	private String digest(String table) {
		return jdbc.queryForObject("select md5(coalesce(string_agg(t::text, '|' order by t::text), '')) from " + table
				+ " t", String.class);
	}
}
