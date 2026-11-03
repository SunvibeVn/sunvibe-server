package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;

/**
 * Content reads over HTTP (survey-media spec 5.4 content GET order, 7.1, ADR-011 decision 2, A-23, A-24, A-30; SM-14,
 * SM-15, SM-26, SM-34; plan Task A17). Needs Docker.
 */
class MediaContentReadApiPostgresTest extends MediaApiTestBase {

	private static final byte[] FULL = MediaApiTestSupport.bytes(5_000, 31);
	private static final byte[] PREVIEW = MediaApiTestSupport.bytes(2_000, 32);
	private static final byte[] THUMBNAIL = MediaApiTestSupport.bytes(500, 33);

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
	private MediaTestStore store;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser admin;
	private TestUser customer;
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0982");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Read Sale", "SALE");
		otherSale = survey.user("Read Other Sale", "SALE");
		leader = survey.user("Read Leader", "SALE_LEADER");
		admin = survey.user("Read Admin", "ADMIN");
		customer = survey.user("Read Customer", "CUSTOMER");
	}

	@BeforeEach
	void startCapture() {
		logs.list.clear();
		logs.start();
		root.addAppender(logs);
		store.reset();
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void stopCapture() {
		root.detachAppender(logs);
		switches.reset();
		store.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	// ==================== SM-15: headers and request rules ====================

	@Test
	void headers_exactlyAsAdr011Decision2() throws Exception {
		Draft draft = draft();
		String id = ready(draft);

		HttpResponse<byte[]> preview = bytes(sale, id, "preview", Map.of());
		HttpResponse<byte[]> download = bytes(sale, id, "full?download=true", Map.of());

		assertEquals(200, preview.statusCode());
		assertEquals(PREVIEW.length, preview.body().length);
		assertEquals("image/jpeg", preview.headers().firstValue("Content-Type").orElseThrow());
		assertEquals("no-store", preview.headers().firstValue("Cache-Control").orElseThrow());
		assertEquals("nosniff", preview.headers().firstValue("X-Content-Type-Options").orElseThrow());
		assertEquals("default-src 'none'; sandbox", preview.headers().firstValue("Content-Security-Policy")
				.orElseThrow());
		assertEquals("same-site", preview.headers().firstValue("Cross-Origin-Resource-Policy").orElseThrow());
		assertEquals("inline; filename=\"photo-" + id + ".jpg\"", preview.headers()
				.firstValue("Content-Disposition").orElseThrow());
		assertTrue(preview.headers().firstValue("Accept-Ranges").isEmpty());
		assertEquals("attachment; filename=\"photo-" + id + ".jpg\"", download.headers()
				.firstValue("Content-Disposition").orElseThrow());
		assertEquals(FULL.length, download.body().length);
	}

	@Test
	void rangeRequest_200WholeNoAcceptRanges() throws Exception {
		String id = ready(draft());

		HttpResponse<byte[]> ranged = bytes(sale, id, "full", Map.of("Range", "bytes=0-9", "If-Range", "x"));

		assertEquals(200, ranged.statusCode());
		assertEquals(FULL.length, ranged.body().length);
		assertTrue(ranged.headers().firstValue("Content-Range").isEmpty());
		assertTrue(ranged.headers().firstValue("Accept-Ranges").isEmpty());
	}

	@Test
	void head_405MethodNotAllowed() throws Exception {
		String id = ready(draft());

		HttpResponse<byte[]> head = checklists.api().http().send(checklists.api().request(sale, MediaApiTestSupport.MEDIA
				+ "/" + id + "/content/full").method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(405, head.statusCode());
		assertEquals("GET", head.headers().firstValue("Allow").orElseThrow());
		assertEquals(0, store.servingReads.get());
	}

	@Test
	void downloadOnPreview_400_downloadMalformed_400_unknownVariant_400() throws Exception {
		String id = ready(draft());

		assertError(media.read(sale, id, "preview?download=true"), 400, "VALIDATION_FAILED", "download");
		assertError(media.read(sale, id, "preview?download=false"), 400, "VALIDATION_FAILED", "download");
		assertError(media.read(sale, id, "full?download=yes"), 400, "VALIDATION_FAILED", "download");
		assertError(media.read(sale, id, "original"), 400, "VALIDATION_FAILED", "variant");
		assertError(media.read(sale, id, "FULL"), 400, "VALIDATION_FAILED", "variant");
		assertEquals(0, store.servingReads.get());
	}

	@Test
	void rawSource_notReadableThroughAnyEndpoint() throws Exception {
		Draft draft = draft();
		String id = ready(draft);
		byte[] source = "RAW-SOURCE-BYTES-THAT-MUST-NEVER-BE-SERVED".getBytes(StandardCharsets.UTF_8);
		store.putCreateOnly(MediaObjectStore.Bucket.QUARANTINE, "quarantine/" + id + "/1", new java.io
				.ByteArrayInputStream(source), source.length);

		for (String variant : List.of("source", "quarantine", "raw", "original", "quarantine%2F" + id,
				"..%2Fsource", "SOURCE")) {
			Result result = media.read(sale, id, variant);
			assertTrue(result.status() >= 400, variant + ": " + result.rawBody());
			assertFalse(result.rawBody().contains("RAW-SOURCE"), variant);
		}
		Result list = media.list(sale, draft.id(), null);
		assertFalse(list.rawBody().contains("quarantine/"), "no key in the list");
	}

	@Test
	void roleForbiddenAndNotFound_carryConnectionClose() throws Exception {
		String id = ready(draft());

		Map<String, String> forbidden = rawGet(customer, MediaApiTestSupport.MEDIA + "/" + id + "/content/full");
		Map<String, String> hidden = rawGet(otherSale, MediaApiTestSupport.MEDIA + "/" + id + "/content/full");

		assertEquals("403", forbidden.get(":status"), forbidden.toString());
		assertEquals("close", forbidden.get("connection"));
		assertEquals("404", hidden.get(":status"), hidden.toString());
		assertEquals("close", hidden.get("connection"));
	}

	@Test
	void noKeyBucketEndpointNameCaptionOrHashInHeadersBodiesOrLogs() throws Exception {
		Draft draft = draft();
		String id = ready(draft);
		jdbc.update("update survey_media set caption = 'Mặt tiền nhà số 12' where id = ?", id);
		String sourceHash = jdbc.queryForObject("select source_sha256 from survey_media where id = ?", String.class,
				id);
		store.delete(MediaObjectStore.Bucket.MEDIA, "media/" + id + "/1/PREVIEW");

		HttpResponse<byte[]> full = bytes(sale, id, "full", Map.of());
		Result missing = media.read(sale, id, "preview");
		Result list = media.list(sale, draft.id(), null);

		assertEquals(200, full.statusCode());
		assertEquals(409, missing.status());
		String headers = full.headers().map().toString() + missing.response().headers().map();
		StringBuilder logged = new StringBuilder();
		logs.list.forEach(event -> logged.append(event.getFormattedMessage()).append('\n'));
		for (String secret : List.of("media/" + id, "quarantine/", sourceHash, "Mặt tiền nhà", "sunvibe-media-api-test")) {
			assertFalse(headers.contains(secret), "header: " + secret);
			assertFalse(missing.rawBody().contains(secret), "body: " + secret);
			assertFalse(logged.toString().contains(secret), "log: " + secret);
		}
		assertFalse(list.rawBody().contains(sourceHash));
		assertTrue(logged.toString().contains(id), "the error log names the photo by id");
	}

	@Test
	void actuatorHealth_showsNoMediaDetail() throws Exception {
		Result health = checklists.api().get(null, "/actuator/health");

		assertFalse(health.rawBody().toLowerCase(Locale.ROOT).contains("media"), health.rawBody());
	}

	// ==================== SM-26: serving matrix ====================

	@Test
	void notReadyRemovedHeldDeletedMissing_409NoRow() throws Exception {
		Draft draft = draft();
		Map<String, String> cases = new LinkedHashMap<>();
		cases.put("processing", media.insertPhotos(draft.id(), sale.userId(), Row.PROCESSING, 1,
				Instant.now().minusSeconds(600), 1000L, "matrix-processing").get(0));
		String removed = ready(draft);
		markRemoved(removed);
		cases.put("removed", removed);
		String held = ready(draft);
		markHeld(held);
		cases.put("held", held);
		String deleted = ready(draft);
		markRemoved(deleted);
		jdbc.update("update survey_media set content_deleted_at = ? where id = ?", Timestamp.from(Instant.now()),
				deleted);
		cases.put("deleted", deleted);
		String missing = ready(draft);
		jdbc.update("update survey_media set content_missing_at = ? where id = ?", Timestamp.from(Instant.now()),
				missing);
		cases.put("missing", missing);

		for (Map.Entry<String, String> entry : cases.entrySet()) {
			for (String variant : List.of("thumbnail", "preview", "full")) {
				assertError(media.read(sale, entry.getValue(), variant), 409, "MEDIA_NOT_AVAILABLE", "status");
			}
		}
		assertEquals(0, store.servingReads.get(), "no store call");
		assertEquals(0, contentRows(draft), "no access row");
	}

	@Test
	void removalThenThumbnailAtOnce_409() throws Exception {
		String id = ready(draft());
		assertEquals(200, bytes(sale, id, "thumbnail", Map.of()).statusCode());

		markRemoved(id);

		assertError(media.read(sale, id, "thumbnail"), 409, "MEDIA_NOT_AVAILABLE", "status");
	}

	@Test
	void holdThenThumbnailAtOnce_409() throws Exception {
		String id = ready(draft());
		assertEquals(200, bytes(sale, id, "thumbnail", Map.of()).statusCode());

		markHeld(id);

		assertError(media.read(sale, id, "thumbnail"), 409, "MEDIA_NOT_AVAILABLE", "status");
	}

	@Test
	void adminReadsHeld_rowMediaHeldRead() throws Exception {
		Draft draft = draft();
		String id = ready(draft);
		markHeld(id);

		for (String variant : List.of("thumbnail", "preview", "full")) {
			assertEquals(200, bytes(admin, id, variant, Map.of()).statusCode(), variant);
		}

		assertEquals(3, jdbc.queryForObject("select count(*) from survey_access_logs where user_id = ? and"
				+ " resource_type = 'MEDIA_HELD_READ' and resource_id = ?", Integer.class, admin.userId(), id));
	}

	@Test
	void adminReadsRemovedHeldContentPresent() throws Exception {
		String id = ready(draft());
		markHeld(id);
		jdbc.update("update survey_media set removed_at = ?, removed_by_user_id = ?, removal_kind = 'HOLDER'"
				+ " where id = ?", Timestamp.from(Instant.now()), sale.userId(), id);

		assertEquals(200, bytes(admin, id, "full", Map.of()).statusCode());
		assertError(media.read(sale, id, "full"), 409, "MEDIA_NOT_AVAILABLE", "status");
	}

	@Test
	void storeNotFound_409WithIdsOnlyLog() throws Exception {
		String id = ready(draft());
		store.delete(MediaObjectStore.Bucket.MEDIA, "media/" + id + "/1/FULL");

		assertError(media.read(sale, id, "full"), 409, "MEDIA_NOT_AVAILABLE", "status");

		assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(id)
				&& !event.getFormattedMessage().contains("media/")));
	}

	@Test
	void storeUnavailable_503NoRow() throws Exception {
		Draft draft = draft();
		String id = ready(draft);
		store.failServing.set(true);

		assertError(media.read(sale, id, "preview"), 503, "MEDIA_STORE_UNAVAILABLE", null);
		assertEquals(0, contentRows(draft));
	}

	// ==================== SM-34: the serving switch ====================

	@Test
	void servingDisabled_503NoSurveyQueryNoStoreCall() throws Exception {
		String id = ready(draft());
		switches.serving(false);

		Result known = media.read(sale, id, "full");
		Result unknown = media.read(sale, "01JNOSUCHMED1A000000000000", "full");
		Result malformed = media.read(sale, id, "original");

		for (Result result : List.of(known, unknown, malformed)) {
			assertError(result, 503, "MEDIA_SERVING_DISABLED", null);
			assertTrue(Long.parseLong(result.header("Retry-After")) > 0);
		}
		assertEquals(0, store.servingReads.get());
	}

	@Test
	void servingDisabled_listsUploadsRemovalHoldProcessingGoOn() throws Exception {
		Draft draft = draft();
		switches.serving(false);
		byte[] bytes = MediaApiTestSupport.bytes(2_000, 34);

		Result list = media.list(sale, draft.id(), null);
		String id = media.created(sale, draft.id(), bytes);
		Result put = media.put(sale, id, bytes);

		assertEquals(200, list.status(), list.rawBody());
		assertFalse(list.body().path("servingEnabled").asBoolean());
		assertEquals(202, put.status(), put.rawBody());
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private String ready(Draft draft) {
		return media.readyWithVariants(store, draft.id(), sale.userId(), FULL, PREVIEW, THUMBNAIL);
	}

	private void markRemoved(String id) {
		jdbc.update("update survey_media set removed_at = ?, removed_by_user_id = ?, removal_kind = 'HOLDER',"
				+ " content_delete_requested_at = ? where id = ?", Timestamp.from(Instant.now()), sale.userId(),
				Timestamp.from(Instant.now()), id);
	}

	private void markHeld(String id) {
		jdbc.update("update survey_media set held_at = ?, held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT'"
				+ " where id = ?", Timestamp.from(Instant.now()), admin.userId(), id);
	}

	private int contentRows(Draft draft) {
		return jdbc.queryForObject("select count(*) from survey_access_logs where lead_id = ? and resource_type in"
				+ " ('MEDIA_PREVIEW', 'MEDIA_FULL', 'MEDIA_DOWNLOAD', 'MEDIA_HELD_READ')", Integer.class,
				draft.visit().leadId());
	}

	private HttpResponse<byte[]> bytes(TestUser caller, String id, String variantAndQuery, Map<String, String> headers)
			throws Exception {
		HttpRequest.Builder request = checklists.api().request(caller, MediaApiTestSupport.MEDIA + "/" + id
				+ "/content/" + variantAndQuery).GET();
		headers.forEach(request::header);
		return checklists.api().http().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
	}

	/** A GET on its own socket, so the Connection header of the answer is visible. */
	private Map<String, String> rawGet(TestUser caller, String path) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(10_000);
			OutputStream out = socket.getOutputStream();
			out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + caller.secret()
					+ "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			InputStream in = socket.getInputStream();
			Map<String, String> head = new LinkedHashMap<>();
			StringBuilder line = new StringBuilder();
			int previous = -1;
			for (int next = in.read(); next != -1; next = in.read()) {
				if (next == '\n' && previous == '\r') {
					String text = line.substring(0, line.length() - 1);
					line.setLength(0);
					if (text.isEmpty()) {
						break;
					}
					if (head.isEmpty()) {
						head.put(":status", text.split(" ")[1]);
					} else {
						int colon = text.indexOf(':');
						head.put(text.substring(0, colon).trim().toLowerCase(Locale.ROOT), text.substring(colon + 1)
								.trim());
					}
				} else {
					line.append((char) next);
				}
				previous = next;
			}
			return head;
		}
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
