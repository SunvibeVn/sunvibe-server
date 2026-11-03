package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.RawUpload;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;

/**
 * The content upload through the real HTTP stack with a Hikari pool of two connections (survey-media spec 4.4 step 2,
 * A-3, A-13; SM-7, SM-20 upload half, SM-22; plan Task A14): no transaction and no connection is held while a body
 * streams, the slots refuse at once with {@code Connection: close} before reading, later answers drain the body so a
 * client sending more than 2 MB still reads them. Its own context (the pool size is a property). Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			MediaApiTestBase.STORE,
			MediaApiTestBase.NO_BACKGROUND_SWEEP,
			MediaApiTestBase.NO_BACKGROUND_SWEEP_INTERVAL,
			MediaApiTestBase.NO_BACKGROUND_PROBES,
			MediaApiTestBase.NO_BACKGROUND_DELETION,
			MediaApiTestBase.NO_BACKGROUND_LISTING,
			MediaApiTestBase.STORE_ROOT,
			MediaApiTestBase.SPOOL_DIR,
			"spring.datasource.hikari.maximum-pool-size=2",
			"spring.datasource.hikari.connection-timeout=10000",
			MediaApiTestBase.NO_PROGRESS,
			MediaApiTestBase.FULL_DEADLINE })
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, MediaTestSwitches.Config.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaUploadHttpStackPostgresTest {

	private static final byte[] PHOTO = MediaApiTestSupport.bytes(3 * 1024 * 1024, 11);
	private static final int STALLED_USERS = 6;

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
	private TestUser leader;
	private TestUser saleA;
	private TestUser saleB;
	private final List<TestUser> staller = new ArrayList<>();
	private final List<String> stallerChecklist = new ArrayList<>();
	private final List<RawUpload> open = new ArrayList<>();
	private int seed = 1000;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0976");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		leader = survey.user("Stack Leader", "SALE_LEADER");
		saleA = survey.user("Stack Sale A", "SALE");
		saleB = survey.user("Stack Sale B", "SALE");
		for (int i = 0; i < STALLED_USERS; i++) {
			TestUser user = survey.user("Stack Staller " + i, "SALE");
			staller.add(user);
			Visit visit = checklists.visited(leader, user);
			stallerChecklist.add(checklists.started(user, visit.appointmentId()));
		}
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void closeUploads() throws Exception {
		for (RawUpload upload : open) {
			upload.close();
		}
		open.clear();
		awaitNoSlots();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== SM-7 ====================

	@Test
	void reassignmentWhileStreaming_commitRefusedObjectDeletedNothingAttached() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);

		try (RawUpload upload = media.openUpload(saleA, id, PHOTO.length, MediaApiTestSupport.digest(PHOTO))) {
			upload.send(Arrays.copyOf(PHOTO, 1024 * 1024));
			awaitStatus(id, "RECEIVING");
			checklists.reassign(leader, draft.visit().leadId(), saleB);
			upload.send(Arrays.copyOfRange(PHOTO, 1024 * 1024, PHOTO.length));
			Map<String, String> answer = upload.answer(30_000);
			// a SALE sees only their own leads: after the reassignment the commit answers 404 (SM-7)
			assertEquals("404", answer.get(":status"), answer.toString());
		}

		assertEquals("AWAITING_CONTENT", status(id), "nothing attached: the claim is released");
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where id = ? and source_storage_key is"
				+ " not null", Integer.class, id));
		assertEquals(List.of(), checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_ADDED"));
	}

	// ==================== SM-22 ====================

	@Test
	void bodiesOver2MB_replay202_409s_503_reachClientDrained() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		assertEquals(202, media.put(saleA, id, PHOTO).status());
		byte[] other = MediaApiTestSupport.bytes(PHOTO.length, 12);

		assertEquals(202, media.put(saleA, id, PHOTO).status(), "the replay");
		assertCode(media.put(saleA, id, other), 409, "MEDIA_CONTENT_ALREADY_RECEIVED");
		byte[] theirs = MediaApiTestSupport.bytes(PHOTO.length, 14);
		String notMine = media.insertPhoto(draft.id(), saleB.userId(), MediaApiTestSupport.Row.AWAITING_CONTENT,
				Instant.now(), MediaApiTestSupport.sha(theirs));
		assertCode(media.put(saleA, notMine, theirs), 409, "MEDIA_NOT_AVAILABLE");
		String fresh = media.created(saleA, draft.id(), other);
		assertCode(media.put(saleA, fresh, "application/octet-stream", MediaApiTestSupport.digest(other),
				MediaApiTestSupport.bytes(PHOTO.length + 1, 13)), 400, "MALFORMED_REQUEST");
		MediaIntakeFixture.pause(jdbc, PauseReason.STORE);
		assertCode(media.put(saleA, fresh, other), 503, "MEDIA_INTAKE_PAUSED");
		MediaIntakeFixture.open(jdbc);
		SurveyApiTestSupport.ok(survey.moveTo(leader, draft.visit().leadId(), "ON_HOLD"));
		assertCode(media.put(saleA, fresh, other), 409, "CHECKLIST_READ_ONLY");
	}

	@Test
	void forbiddenAndNotFound_closeConnectionWithoutReading() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);

		try (RawUpload hidden = media.openUpload(saleB, id, PHOTO.length, MediaApiTestSupport.digest(PHOTO))) {
			Map<String, String> answer = hidden.answer(5_000);
			assertEquals("404", answer.get(":status"), "answered before a body byte was sent: " + answer);
			assertEquals("close", answer.get("connection"));
			assertTrue(hidden.closedWhileSending(PHOTO), "the container closes after its 2 MB swallow limit");
		}
		TestUser manager = survey.user("Stack Manager", "SALES_MANAGER");
		try (RawUpload refused = media.openUpload(manager, id, PHOTO.length, MediaApiTestSupport.digest(PHOTO))) {
			Map<String, String> answer = refused.answer(5_000);
			assertEquals("403", answer.get(":status"), "answered before a body byte was sent: " + answer);
			assertEquals("close", answer.get("connection"));
			assertTrue(refused.closedWhileSending(PHOTO), "the container closes after its 2 MB swallow limit");
		}
	}

	/**
	 * SM-22 as resolved 2026-11-03 (spec conflict with 4.4 step 2, 5.4 (3b), A-3): the expensive-outcome cap is decided
	 * before the body slots, so it answers before any body byte is read, with Connection: close and Retry-After; draining
	 * there would read a body outside both slots.
	 */
	@Test
	void expensiveCap_429AtOnceConnectionCloseBodyUnread() throws Exception {
		TestUser capped = survey.user("Stack Capped Sale", "SALE");
		Visit visit = checklists.visited(leader, capped);
		String checklistId = checklists.started(capped, visit.appointmentId());
		String id = media.created(capped, checklistId, PHOTO);
		Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
		for (Instant start : List.of(hour, hour.plus(Duration.ofHours(1)))) {
			jdbc.update("insert into survey_media_read_budgets (user_id, hour_start, kind, read_count) values (?, ?,"
					+ " 'EXPENSIVE', 20)", capped.userId(), Timestamp.from(start));
		}

		try (RawUpload upload = media.openUpload(capped, id, PHOTO.length, MediaApiTestSupport.digest(PHOTO))) {
			Map<String, String> answer = upload.answer(5_000);
			assertEquals("429", answer.get(":status"), "answered before a body byte was sent: " + answer);
			assertEquals("close", answer.get("connection"));
			assertTrue(Long.parseLong(answer.get("retry-after")) > 0);
			String body = upload.answerBody(5_000);
			assertTrue(body.contains("\"code\":\"RATE_LIMITED\"") && body.contains("\"field\":\"expensive\""), body);
			assertTrue(upload.closedWhileSending(PHOTO), "the container closes after its 2 MB swallow limit");
		} finally {
			jdbc.update("delete from survey_media_read_budgets where user_id = ?", capped.userId());
		}
		assertEquals("AWAITING_CONTENT", status(id), "refused before the claim");
	}

	// ==================== SM-20 ====================

	@Test
	void threeStalledUploads_holdNoConnection_reassignmentCompletes() throws Exception {
		Draft draft = draft(saleA);
		for (int i = 0; i < 3; i++) {
			stall(saleA, draft.id(), 20 + i);
		}

		long start = System.nanoTime();
		checklists.reassign(leader, draft.visit().leadId(), saleB);
		long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertTrue(millis < 5_000, "the reassignment found a connection: " + millis + " ms");
	}

	@Test
	void fourthConcurrentPutOfUser_429ConnectionCloseBodyUnread() throws Exception {
		Draft draft = draft(saleA);
		for (int i = 0; i < 3; i++) {
			stall(saleA, draft.id(), 30 + i);
		}
		String fourth = media.created(saleA, draft.id(), MediaApiTestSupport.bytes(1000, 34));

		try (RawUpload upload = media.openUpload(saleA, fourth, 1000, MediaApiTestSupport.digest(
				MediaApiTestSupport.bytes(1000, 34)))) {
			Map<String, String> answer = upload.answer(5_000);
			assertEquals("429", answer.get(":status"), answer.toString());
			assertEquals("close", answer.get("connection"));
			assertTrue(Long.parseLong(answer.get("retry-after")) > 0);
			upload.send(MediaApiTestSupport.bytes(1000, 34));
			assertTrue(upload.closedByServer(5_000), "closed once the declared body ends, never read by the API");
		}
		assertEquals("AWAITING_CONTENT", status(fourth), "refused before the claim");
	}

	@Test
	void seventeenthPutOnInstance_503ServerBusy() throws Exception {
		stallSixteen();
		byte[] small = MediaApiTestSupport.bytes(1000, seed++);
		String last = media.created(staller.get(5), stallerChecklist.get(5), small);

		try (RawUpload upload = media.openUpload(staller.get(5), last, 1000, MediaApiTestSupport.digest(small))) {
			Map<String, String> answer = upload.answer(5_000);
			assertEquals("503", answer.get(":status"), answer.toString());
			assertEquals("close", answer.get("connection"));
			assertTrue(Long.parseLong(answer.get("retry-after")) > 0);
		}
	}

	@Test
	void loginAndLeadEndpoints_answerWhile16BodiesStall() throws Exception {
		stallSixteen();
		TestUser fresh = checklists.api().createUser("Stack Fresh Login", UserStatus.ACTIVE, "SALE");
		try {
			long loginStart = System.nanoTime();
			TestUser loggedIn = checklists.api().logIn(fresh);
			long loginMillis = Duration.ofNanos(System.nanoTime() - loginStart).toMillis();
			long leadsStart = System.nanoTime();
			Result leads = checklists.api().get(leader, SurveyApiTestSupport.LEADS);
			long leadsMillis = Duration.ofNanos(System.nanoTime() - leadsStart).toMillis();

			// SV-066: the timings are recorded in the test output
			System.out.println("SV-066 while 16 bodies stall: login " + loginMillis + " ms, lead list " + leadsMillis
					+ " ms");
			assertFalse(loggedIn.secret().isBlank());
			assertEquals(200, leads.status(), leads.rawBody());
			assertTrue(loginMillis < 5_000 && leadsMillis < 5_000, loginMillis + " / " + leadsMillis);
		} finally {
			AcquisitionApiTestSupport.deleteUsers(jdbc, List.of(fresh.userId()));
		}
	}

	@Test
	void slotOfKilledInstance_expiresAfter11Minutes() throws Exception {
		Draft draft = draft(saleA);
		byte[] small = MediaApiTestSupport.bytes(1000, 60);
		String id = media.created(saleA, draft.id(), small);
		Instant now = Instant.now();
		for (int i = 0; i < 3; i++) {
			jdbc.update("insert into survey_media_body_slots (user_id, instance_id, taken_at, expires_at)"
					+ " values (?, '01JKILLEDINSTANCE000000001', ?, ?)", saleA.userId(),
					Timestamp.from(now.minus(Duration.ofMinutes(12))), Timestamp.from(now.minus(Duration.ofMinutes(1))));
		}

		assertEquals(202, media.put(saleA, id, small).status(), "expired slots of a killed instance do not count");

		jdbc.update("delete from survey_media_body_slots where user_id = ?", saleA.userId());
		for (int i = 0; i < 3; i++) {
			jdbc.update("insert into survey_media_body_slots (user_id, instance_id, taken_at, expires_at)"
					+ " values (?, '01JKILLEDINSTANCE000000001', ?, ?)", saleA.userId(),
					Timestamp.from(now.minus(Duration.ofMinutes(1))), Timestamp.from(now.plus(Duration.ofMinutes(10))));
		}
		String next = media.created(saleA, draft.id(), MediaApiTestSupport.bytes(1000, 61));
		try (RawUpload upload = media.openUpload(saleA, next, 1000, MediaApiTestSupport.digest(
				MediaApiTestSupport.bytes(1000, 61)))) {
			assertEquals("429", upload.answer(5_000).get(":status"), "live slots of another instance count");
		} finally {
			jdbc.update("delete from survey_media_body_slots where user_id = ?", saleA.userId());
		}
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft(TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	/** An upload of the user that sent its headers and 1 KiB, then stalls inside its body slots. */
	private void stall(TestUser user, String checklistId, int seed) throws Exception {
		byte[] bytes = MediaApiTestSupport.bytes(64 * 1024, seed);
		String id = media.created(user, checklistId, bytes);
		RawUpload upload = media.openUpload(user, id, bytes.length, MediaApiTestSupport.digest(bytes));
		open.add(upload);
		upload.send(Arrays.copyOf(bytes, 1024));
		awaitStatus(id, "RECEIVING");
	}

	/** 16 stalled bodies of 6 users: 3, 3, 3, 3, 2, 2; every file new. */
	private void stallSixteen() throws Exception {
		for (int user = 0; user < STALLED_USERS; user++) {
			for (int n = 0; n < (user < 4 ? 3 : 2); n++) {
				stall(staller.get(user), stallerChecklist.get(user), seed++);
			}
		}
	}

	private String status(String mediaId) {
		return jdbc.queryForObject("select status from survey_media where id = ?", String.class, mediaId);
	}

	private void awaitStatus(String mediaId, String expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while (!expected.equals(status(mediaId))) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError(mediaId + " never became " + expected + " but " + status(mediaId));
			}
			Thread.sleep(20);
		}
	}

	/** Closed uploads end with a read error: their slots are freed in finally. */
	private void awaitNoSlots() throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (jdbc.queryForObject("select count(*) from survey_media_body_slots where instance_id <> "
				+ "'01JKILLEDINSTANCE000000001'", Integer.class) > 0) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("body slots were not freed");
			}
			Thread.sleep(50);
		}
	}

	private static void assertCode(Result result, int status, String code) {
		assertEquals(status, result.status(), result.rawBody());
		assertEquals(code, result.body().path("code").asString(), result.rawBody());
	}
}
