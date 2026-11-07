package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.RawUpload;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.service.media.MediaProbeRunner;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;

/**
 * The content upload over HTTP (survey-media spec 4.4 step 2, the content order of 5.4, 7.1..7.3; SM-5, SM-9, SM-12,
 * SM-16, SM-23 upload parts; A-13; plan Task A14). Needs Docker.
 */
class MediaContentPutApiPostgresTest extends MediaApiTestBase {

	private static final byte[] PHOTO = MediaApiTestSupport.bytes(4096, 1);

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
	private MediaTestSwitches switches;

	@Autowired
	private MediaProperties mediaProperties;

	@Autowired
	private MediaProbeRunner probes;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser manager;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0975");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		saleA = survey.user("Content Sale A", "SALE");
		saleB = survey.user("Content Sale B", "SALE");
		otherSale = survey.user("Content Other Sale", "SALE");
		leader = survey.user("Content Leader", "SALE_LEADER");
		manager = survey.user("Content Manager", "SALES_MANAGER");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void resetSwitches() {
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== SM-5 ====================

	@Test
	void put20MiB_202Processing() throws Exception {
		Draft draft = draft(saleA);
		byte[] big = MediaApiTestSupport.bytes(20_971_520, 2);
		String id = media.created(saleA, draft.id(), big);

		Result put = media.put(saleA, id, big);

		assertEquals(202, put.status(), put.rawBody());
		assertEquals(List.of("media", "setVersion", "historyEntry"), AcquisitionApiTestSupport.fieldNames(
				put.body()));
		JsonNode item = put.body().path("media");
		assertEquals("PROCESSING", item.path("status").asString());
		assertFalse(item.path("contentReceivedAt").isNull());
		assertEquals("NOT_READY", item.path("unavailableReason").asString());
		Map<String, Object> row = jdbc.queryForMap("select status, source_storage_key, content_received_at from"
				+ " survey_media where id = ?", id);
		assertEquals("PROCESSING", row.get("status"));
		assertEquals(1, storedObjects(id), "the source is in quarantine");
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media_jobs where media_id = ? and kind ="
				+ " 'PROCESS' and state = 'PENDING'", Integer.class, id));
		assertEquals(0, slots(saleA), "the body slot is freed");
	}

	@Test
	void putOver20MiB_413AtOnce() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);

		try (RawUpload upload = media.openUpload(saleA, id, 20_971_521L, MediaApiTestSupport.digest(PHOTO))) {
			Map<String, String> answer = upload.answer(10_000);
			assertEquals("413", answer.get(":status"), answer.toString());
			assertEquals("close", answer.get("connection"));
		}
	}

	// ==================== SM-9 ====================

	@Test
	void lengthOtherThanSize_400() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		byte[] longer = MediaApiTestSupport.bytes(4097, 1);

		assertError(media.put(saleA, id, "application/octet-stream", MediaApiTestSupport.digest(PHOTO), longer), 400,
				"MALFORMED_REQUEST", "Content-Length");
	}

	@Test
	void digestOtherThanDeclared_400() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		byte[] other = MediaApiTestSupport.bytes(4096, 99);

		assertError(media.put(saleA, id, other), 400, "MALFORMED_REQUEST", "Content-Digest");
	}

	@Test
	void bodyNotMatchingDigest_400NothingStored() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		byte[] tampered = PHOTO.clone();
		tampered[100] ^= 1;

		assertError(media.put(saleA, id, "application/octet-stream", MediaApiTestSupport.digest(PHOTO), tampered),
				400, "MALFORMED_REQUEST", "Content-Digest");

		assertEquals(0, storedObjects(id));
		assertEquals("AWAITING_CONTENT", status(id), "the claim is released");
	}

	@Test
	void sameDigestAfterSuccess_202NoHistory() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		assertEquals(202, media.put(saleA, id, PHOTO).status());

		Result again = media.put(saleA, id, PHOTO);

		assertEquals(202, again.status(), again.rawBody());
		assertTrue(again.body().path("historyEntry").isNull());
		assertEquals(1, checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_ADDED").size());
	}

	@Test
	void otherDigestAfterSuccess_409() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		assertEquals(202, media.put(saleA, id, PHOTO).status());

		assertError(media.put(saleA, id, MediaApiTestSupport.bytes(4096, 3)), 409, "MEDIA_CONTENT_ALREADY_RECEIVED",
				null);
	}

	@Test
	void claimAfter60Minutes_409ExpiredBeforeSweep() throws Exception {
		Draft draft = draft(saleA);
		byte[] thousand = MediaApiTestSupport.bytes(1000, 4);
		String id = media.insertPhoto(draft.id(), saleA.userId(), Row.AWAITING_CONTENT,
				Instant.now().minus(Duration.ofMinutes(61)), MediaApiTestSupport.sha(thousand));

		assertError(media.put(saleA, id, thousand), 409, "MEDIA_UPLOAD_EXPIRED", null);
		assertEquals("AWAITING_CONTENT", status(id), "the sweep expires it later");
	}

	@Test
	void notCreator_409FieldUploadedBy() throws Exception {
		Draft draft = draft(saleA);
		byte[] thousand = MediaApiTestSupport.bytes(1000, 5);
		String id = media.insertPhoto(draft.id(), saleB.userId(), Row.AWAITING_CONTENT, Instant.now(),
				MediaApiTestSupport.sha(thousand));

		assertError(media.put(saleA, id, thousand), 409, "MEDIA_NOT_AVAILABLE", "uploadedBy");
	}

	@Test
	void removedPhoto_409FieldStatus() throws Exception {
		Draft draft = draft(saleA);
		byte[] thousand = MediaApiTestSupport.bytes(1000, 6);
		String id = media.insertPhoto(draft.id(), saleA.userId(), Row.REMOVED_READY, Instant.now(),
				MediaApiTestSupport.sha(thousand));

		assertError(media.put(saleA, id, thousand), 409, "MEDIA_NOT_AVAILABLE", "status");
	}

	// ==================== SM-23 ====================

	@Test
	void uploadsOff_409() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		switches.uploads(false);

		assertError(media.put(saleA, id, PHOTO), 409, "MEDIA_UPLOAD_DISABLED", null);
		assertEquals("AWAITING_CONTENT", status(id));
	}

	@Test
	void paused_503() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		MediaIntakeFixture.pause(jdbc, PauseReason.STORE);

		assertError(media.put(saleA, id, PHOTO), 503, "MEDIA_INTAKE_PAUSED", null);
		assertEquals(0, storedObjects(id));
	}

	// ==================== SM-16 ====================

	@Test
	void commit_writesMediaAddedHistoryWithCategoryAndMovesLastUpdate_leadVersionUnchanged() throws Exception {
		Draft draft = draft(saleA);
		String leadId = draft.visit().leadId();
		String id = media.created(saleA, draft.id(), PHOTO);
		long version = survey.version(leadId);
		String status = survey.leadStatus(leadId);
		Instant lastUpdate = lastActivityAt(leadId);

		Result put = media.put(saleA, id, PHOTO);

		assertEquals(202, put.status(), put.rawBody());
		List<Map<String, Object>> added = checklists.activities(leadId, "CHECKLIST_MEDIA_ADDED");
		assertEquals(1, added.size());
		assertEquals("EXTERIOR_ACCESS", added.get(0).get("reference_detail"));
		assertEquals(draft.id(), added.get(0).get("reference_id"));
		assertEquals("CHECKLIST_MEDIA_ADDED", put.body().path("historyEntry").path("type").asString());
		assertTrue(lastActivityAt(leadId).isAfter(lastUpdate));
		assertEquals(version, survey.version(leadId), "the lead version stays (I-5)");
		assertEquals(status, survey.leadStatus(leadId));
	}

	// ==================== SM-12 commit part ====================

	@Test
	void commit_otherVisitCommittedMeanwhile_409FromOtherVisit() throws Exception {
		Draft first = draft(saleA);
		String leadId = first.visit().leadId();
		String second = resurveyChecklist(leadId);
		String id = media.created(saleA, second, PHOTO);

		String earlier;
		try (RawUpload upload = media.openUpload(saleA, id, PHOTO.length, MediaApiTestSupport.digest(PHOTO))) {
			upload.send(java.util.Arrays.copyOf(PHOTO, 2048));
			awaitStatus(id, "RECEIVING");
			earlier = media.insertPhoto(first.id(), saleA.userId(), Row.READY, Instant.now().minus(
					Duration.ofMinutes(5)), MediaApiTestSupport.sha(PHOTO));
			upload.send(java.util.Arrays.copyOfRange(PHOTO, 2048, PHOTO.length));
			Map<String, String> answer = upload.answer(30_000);
			assertEquals("409", answer.get(":status"), answer.toString());
		}

		assertEquals("AWAITING_CONTENT", status(id));
		assertEquals(0, storedObjects(id), "the attempt's object is deleted");
		Result again = media.put(saleA, id, PHOTO);
		assertError(again, 409, "MEDIA_FROM_OTHER_VISIT", "contentSha256");
		assertEquals(earlier, again.body().path("errors").path(0).path("allowedValues").path(0).asString());
	}

	// ==================== store failure ====================

	@Test
	void storeFailure_503StoreUnavailableAndProbeRequested() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);
		probes.poll();
		Path root = Path.of(mediaProperties.getLocal().getRoot());
		Path aside = root.resolveSibling("store-aside");
		Files.createDirectories(root);
		Files.move(root, aside);
		Files.writeString(root, "not a directory");
		Result put;
		try {
			put = media.put(saleA, id, PHOTO);
		} finally {
			Files.delete(root);
			Files.move(aside, root);
		}

		assertError(put, 503, "MEDIA_STORE_UNAVAILABLE", null);
		assertTrue(probes.isStoreProbeRequested(), "a store probe is requested (spec A-18)");
		assertEquals("AWAITING_CONTENT", status(id));
		assertEquals(202, media.put(saleA, id, PHOTO).status(), "the same PUT is retried");
	}

	// ==================== A-13: at-once answers close the connection ====================

	@Test
	void notFound_carriesConnectionClose() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);

		for (String target : List.of(id, "01JNOSUCHMED1A000000000000")) {
			try (RawUpload upload = media.openUpload(otherSale, target, 20_000_000L,
					MediaApiTestSupport.digest(PHOTO))) {
				Map<String, String> answer = upload.answer(10_000);
				assertEquals("404", answer.get(":status"), answer.toString());
				assertEquals("close", answer.get("connection"));
			}
		}
	}

	@Test
	void forbidden_carriesConnectionClose() throws Exception {
		Draft draft = draft(saleA);
		String id = media.created(saleA, draft.id(), PHOTO);

		try (RawUpload upload = media.openUpload(manager, id, 20_000_000L, MediaApiTestSupport.digest(PHOTO))) {
			Map<String, String> answer = upload.answer(10_000);
			assertEquals("403", answer.get(":status"), answer.toString());
			assertEquals("close", answer.get("connection"));
		}
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft(TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	/** Re-surveys the lead (survey-schedule): its first checklist becomes read-only NEWER_VISIT; a new one starts. */
	private String resurveyChecklist(String leadId) throws Exception {
		SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "ON_HOLD"));
		SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "CONTACTED"));
		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(30));
		body.put("resurveyReason", "Owner rebuilt the kitchen");
		Result booked = survey.book(saleA, leadId, body);
		assertEquals(201, booked.status(), booked.rawBody());
		String resurvey = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		survey.shiftBack(resurvey, Duration.ofDays(2));
		Instant start = jdbc.queryForObject("select start_at from survey_appointments where id = ?", Timestamp.class,
				resurvey).toInstant();
		assertEquals(200, checklists.recordVisit(saleA, leadId, resurvey, start).status());
		return checklists.started(saleA, resurvey);
	}

	private String status(String mediaId) {
		return jdbc.queryForObject("select status from survey_media where id = ?", String.class, mediaId);
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

	private int slots(TestUser user) {
		return jdbc.queryForObject("select count(*) from survey_media_body_slots where user_id = ?", Integer.class,
				user.userId());
	}

	/** Objects of the photo in the local store (every bucket). */
	private long storedObjects(String mediaId) throws Exception {
		Path root = Path.of(mediaProperties.getLocal().getRoot());
		if (!Files.isDirectory(root)) {
			return 0;
		}
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(Files::isRegularFile).filter(path -> path.toString().contains(mediaId)).count();
		}
	}

	private Instant lastActivityAt(String leadId) {
		return jdbc.queryForObject("select max(occurred_at) from owner_lead_activities where lead_id = ?",
				Timestamp.class, leadId).toInstant();
	}

	private static void assertError(Result result, int status, String code, String fieldOrNull) {
		assertEquals(status, result.status(), result.rawBody());
		assertEquals(code, result.body().path("code").asString(), result.rawBody());
		if (fieldOrNull == null) {
			assertEquals(0, result.body().path("errors").size(), result.rawBody());
		} else {
			assertEquals(fieldOrNull, result.body().path("errors").path(0).path("field").asString(),
					result.rawBody());
		}
	}
}
