package vn.sunvibe.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;

/**
 * Create a photo record over HTTP (survey-media spec 4.4 step 1, 5.4 create order, 7.1..7.3; SM-5, SM-6, SM-9, SM-23,
 * SM-24 create parts; plan Task A13). Needs Docker.
 */
class MediaCreateApiPostgresTest extends MediaApiTestBase {

	private static final String ULID = "[0-9A-HJKMNP-TV-Z]{26}";
	private static final List<String> ITEM_FIELDS = List.of("id", "mediaNo", "status", "statusReason", "spaceId",
			"spaceCodeAtUpload", "category", "caption", "captionHidden", "uploadedByUserId", "uploadedByName",
			"createdAt", "contentReceivedAt", "readyAt", "width", "height", "removed", "held", "contentAvailable",
			"unavailableReason", "carriedFromChecklistNo");

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

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;
	private final List<TestUser> outsiders = new ArrayList<>();

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0971");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		saleA = survey.user("Media Sale A", "SALE");
		saleB = survey.user("Media Sale B", "SALE");
		otherSale = survey.user("Media Other Sale", "SALE");
		leader = survey.user("Media Leader", "SALE_LEADER");
		manager = survey.user("Media Manager", "SALES_MANAGER");
		admin = survey.user("Media Admin", "ADMIN");
		for (String role : List.of("LISTING_REVIEWER", "CUSTOMER", "OWNER")) {
			outsiders.add(survey.user("Media Outsider " + role, role));
		}
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

	// ==================== the answer ====================

	@Test
	void create_201_answersTheItemAndSetVersion_noHistory() throws Exception {
		Draft draft = draft(saleA);

		Result created = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("answer")));

		assertEquals(201, created.status(), created.rawBody());
		JsonNode item = created.body().path("media");
		assertEquals(List.of("media", "setVersion"), AcquisitionApiTestSupport.fieldNames(created.body()));
		assertEquals(ITEM_FIELDS, AcquisitionApiTestSupport.fieldNames(item));
		assertTrue(item.path("id").asString().matches(ULID));
		assertEquals(1, item.path("mediaNo").asInt());
		assertEquals("AWAITING_CONTENT", item.path("status").asString());
		assertEquals(saleA.userId(), item.path("uploadedByUserId").asString());
		assertEquals("Media Sale A", item.path("uploadedByName").asString());
		assertFalse(item.path("contentAvailable").asBoolean());
		assertEquals("NOT_READY", item.path("unavailableReason").asString());
		assertTrue(item.path("removed").isNull());
		assertTrue(item.path("held").isNull());
		assertEquals(1, created.body().path("setVersion").asLong());
		assertEquals(1, media.setVersion(draft.id()));
		assertEquals(List.of(), checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_ADDED"),
				"no History at create (spec 4.4 step 1)");
		assertFalse(created.rawBody().contains(MediaApiTestSupport.sha("answer")), "the hash is never returned");
	}

	// ==================== SM-5 ====================

	@Test
	void create_20MiB_accepted_oneMore_400() throws Exception {
		Draft draft = draft(saleA);

		assertEquals(201, media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(),
				null, "EXTERIOR_ACCESS", null, 20_971_520L, MediaApiTestSupport.sha("20MiB"))).status());
		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", null, 20_971_521L, MediaApiTestSupport.sha("20MiB+1"))), 400, "VALIDATION_FAILED",
				"sizeBytes");
	}

	@Test
	void create_100thAccepted_101st409() throws Exception {
		Draft draft = draft(saleA);
		Instant earlier = Instant.now().minus(Duration.ofHours(2));
		media.insertPhotos(draft.id(), saleA.userId(), Row.READY, 80, earlier, 1000L, "hundred-ready");
		media.insertPhotos(draft.id(), saleA.userId(), Row.AWAITING_CONTENT, 19, earlier, 1000L, "hundred-pending");

		Result hundredth = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"hundredth")));
		Result next = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"hundred-and-first")));

		assertEquals(201, hundredth.status(), hundredth.rawBody());
		assertError(next, 409, "MEDIA_LIMIT_REACHED", "selected");
	}

	@Test
	void removedRejectedFailedCancelledExpired_freeTheirPlace() throws Exception {
		Draft draft = draft(saleA);
		Instant earlier = Instant.now().minus(Duration.ofHours(2));
		media.insertPhotos(draft.id(), saleA.userId(), Row.READY, 80, earlier, 1000L, "free-ready");
		media.insertPhotos(draft.id(), saleA.userId(), Row.AWAITING_CONTENT, 19, earlier, 1000L, "free-pending");
		for (Row row : List.of(Row.REMOVED_READY, Row.REJECTED, Row.FAILED, Row.CANCELLED, Row.UPLOAD_EXPIRED)) {
			media.insertPhotos(draft.id(), saleA.userId(), row, 5, earlier, 1000L, "free-" + row);
		}

		Result created = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"free-100th")));

		assertEquals(201, created.status(), created.rawBody());
	}

	@Test
	void twentyFirstPending_409() throws Exception {
		Draft draft = draft(saleA);
		media.insertPhotos(draft.id(), saleA.userId(), Row.AWAITING_CONTENT, 10, Instant.now().minus(
				Duration.ofHours(2)), 1000L, "pending-awaiting");
		media.insertPhotos(draft.id(), saleA.userId(), Row.PROCESSING, 10, Instant.now().minus(Duration.ofHours(2)),
				1000L, "pending-processing");

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"pending-21st"))), 409, "MEDIA_LIMIT_REACHED", "pending");
	}

	@Test
	void hourly300RecordsAnd3GiB_429WithRetryAfter() throws Exception {
		TestUser records = survey.user("Media Hourly Records", "SALE");
		Draft recordsDraft = draft(records);
		media.insertPhotos(recordsDraft.id(), records.userId(), Row.UPLOAD_EXPIRED, 300,
				Instant.now().minus(Duration.ofMinutes(30)), 1000L, "hourly-records");

		Result refused = media.create(records, recordsDraft.id(), MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("hourly-records-301")));

		assertError(refused, 429, "RATE_LIMITED", "records");
		long retryAfter = Long.parseLong(refused.header("Retry-After"));
		assertTrue(retryAfter > 1700 && retryAfter <= 1800, "until the oldest leaves the hour: " + retryAfter);

		TestUser bytes = survey.user("Media Hourly Bytes", "SALE");
		Draft bytesDraft = draft(bytes);
		media.insertPhotos(bytesDraft.id(), bytes.userId(), Row.UPLOAD_EXPIRED, 153,
				Instant.now().minus(Duration.ofMinutes(10)), 20_971_520L, "hourly-bytes");

		Result tooMany = media.create(bytes, bytesDraft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(),
				null, "EXTERIOR_ACCESS", null, 20_971_520L, MediaApiTestSupport.sha("hourly-bytes-next")));

		assertError(tooMany, 429, "RATE_LIMITED", "bytes");
		assertTrue(Long.parseLong(tooMany.header("Retry-After")) > 0);
		assertEquals(201, media.create(bytes, bytesDraft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(),
				null, "EXTERIOR_ACCESS", null, 1000L, MediaApiTestSupport.sha("hourly-bytes-small"))).status(),
				"a smaller file still fits under 3 GiB");
	}

	// ==================== SM-6 ====================

	@Test
	void anotherSale_404SameBodyMissingOrHidden_withEvent() throws Exception {
		Draft draft = draft(saleA);
		Map<String, Object> body = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("hidden"));

		Result hidden = media.create(otherSale, draft.id(), body);
		Result missing = media.create(otherSale, "01JNOSUCHCHECK1ST000000000", body);
		Result malformed = media.create(otherSale, "not-an-id", body);

		assertError(hidden, 404, "NOT_FOUND", null);
		assertEquals(strip(missing.body()), strip(hidden.body()));
		assertEquals(strip(malformed.body()), strip(hidden.body()));
		List<Map<String, Object>> events = media.mediaEvents(draft.visit().leadId());
		assertEquals(1, events.size(), events.toString());
		assertEquals(otherSale.userId(), events.get(0).get("user_id"));
		assertEquals("MEDIA_SET", events.get(0).get("resource_type"));
		assertEquals(draft.id(), events.get(0).get("resource_id"));
		assertEquals("UPLOAD", events.get(0).get("action"));
		assertEquals("NOT_VISIBLE", events.get(0).get("outcome"));
		assertEquals(List.of(), media.photos(draft.id()));
	}

	@Test
	void eventsCarryRequestId() throws Exception {
		Draft draft = draft(saleA);

		assertEquals(404, media.create(otherSale, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"request-id"))).status());
		assertEquals(404, media.create(otherSale, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"request-id"))).status());

		List<Map<String, Object>> events = media.mediaEvents(draft.visit().leadId());
		assertEquals(1, events.size(), "spec A-19: one event of a key a minute: " + events);
		assertThat((String) events.get(0).get("request_id")).matches(ULID);
	}

	@Test
	void manager_403WithEvent() throws Exception {
		Draft draft = draft(saleA);

		Result managerRefused = media.create(manager, draft.id(), MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("manager")));
		Result adminRefused = media.create(admin, draft.id(), MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha("admin")));

		assertError(managerRefused, 403, "FORBIDDEN", null);
		assertError(adminRefused, 403, "FORBIDDEN", null);
		List<Map<String, Object>> events = media.mediaEvents(draft.visit().leadId());
		assertEquals(List.of("NOT_ASSIGNEE", "NOT_ASSIGNEE"), events.stream().map(e -> e.get("outcome")).toList());
		assertEquals(List.of(manager.userId(), admin.userId()), events.stream().map(e -> e.get("user_id")).toList());
	}

	@Test
	void listingReviewerOwnerCustomer_403OnCreate() throws Exception {
		Draft draft = draft(saleA);

		for (TestUser outsider : outsiders) {
			assertError(media.create(outsider, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
					"outsider"))), 403, "FORBIDDEN", null);
		}
		assertEquals(List.of(), media.mediaEvents(draft.visit().leadId()), "a role 403 writes no event");
	}

	@Test
	void noToken_401() throws Exception {
		Draft draft = draft(saleA);

		Result result = checklists.api().send(HttpRequest.newBuilder(checklists.api().uri(
				ChecklistApiTestSupport.CHECKLISTS + "/" + draft.id() + "/media"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}")).build());

		assertError(result, 401, "UNAUTHORIZED", null);
	}

	@Test
	void nonHolderAssignee_409TakeoverRequired() throws Exception {
		Draft draft = draft(saleA);
		checklists.reassign(leader, draft.visit().leadId(), saleB);

		assertError(media.create(saleB, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"takeover"))), 409, "CHECKLIST_TAKEOVER_REQUIRED", null);
	}

	@Test
	void readOnlyChecklist_409() throws Exception {
		Draft draft = draft(saleA);
		SurveyApiTestSupport.ok(survey.moveTo(leader, draft.visit().leadId(), "ON_HOLD"));

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"read-only"))), 409, "CHECKLIST_READ_ONLY", null);
	}

	@Test
	void ineligibleAssignee_422() throws Exception {
		TestUser leaving = survey.user("Media Leaving Sale", "SALE");
		Draft draft = draft(leaving);
		checklists.makeIneligible(leaving);

		assertError(media.create(leaving, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"ineligible"))), 422, "SURVEYOR_INELIGIBLE", null);
	}

	// ==================== SM-6, SM-23: switches and intake ====================

	@Test
	void uploadsNeverOn_409AndNoRow() throws Exception {
		Draft draft = draft(saleA);
		switches.uploads(false);

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"never-on"))), 409, "MEDIA_UPLOAD_DISABLED", null);

		assertEquals(0, media.setRows(draft.id()), "no media-set row: the rollback window of spec 7.4 stays");
		assertEquals(List.of(), media.photos(draft.id()));
	}

	@Test
	void uploadsSwitchedOffWithPhotos_create409_replayStill200() throws Exception {
		Draft draft = draft(saleA);
		Map<String, Object> body = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("switched-off"));
		assertEquals(201, media.create(saleA, draft.id(), body).status());
		switches.uploads(false);

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"switched-off-2"))), 409, "MEDIA_UPLOAD_DISABLED", null);
		assertEquals(200, media.create(saleA, draft.id(), body).status(), "the replay answers before the switch");
		assertEquals(1, media.photos(draft.id()).size());
	}

	@Test
	void intakePaused_503() throws Exception {
		Draft draft = draft(saleA);
		MediaIntakeFixture.pause(jdbc, PauseReason.STORE);

		Result paused = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"paused")));

		assertError(paused, 503, "MEDIA_INTAKE_PAUSED", null);
		assertEquals(List.of(), media.photos(draft.id()));
	}

	// ==================== SM-9 ====================

	@Test
	void sameKeySameBody_200Replay() throws Exception {
		Draft draft = draft(saleA);
		Map<String, Object> body = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("replay"));

		Result first = media.create(saleA, draft.id(), body);
		Result again = media.create(saleA, draft.id(), body);

		assertEquals(201, first.status(), first.rawBody());
		assertEquals(200, again.status(), again.rawBody());
		assertEquals(first.body().path("media").path("id").asString(), again.body().path("media").path("id")
				.asString());
		assertEquals(1, media.photos(draft.id()).size());
		assertEquals(1, media.setVersion(draft.id()), "a replay bumps nothing");
	}

	@Test
	void sameKeyOtherBody_409() throws Exception {
		Draft draft = draft(saleA);
		Map<String, Object> body = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("key-reused"));
		assertEquals(201, media.create(saleA, draft.id(), body).status());
		Map<String, Object> other = new LinkedHashMap<>(body);
		other.put("caption", "Cổng chính");

		assertError(media.create(saleA, draft.id(), other), 409, "IDEMPOTENCY_KEY_REUSED", "uploadKey");
	}

	@Test
	void sameKeyOtherChecklist_independent() throws Exception {
		Draft first = draft(saleA);
		Draft second = draft(saleA);
		String key = MediaApiTestSupport.newKey();

		assertEquals(201, media.create(saleA, first.id(), MediaApiTestSupport.body(key, null, "EXTERIOR_ACCESS",
				null, 1000L, MediaApiTestSupport.sha("independent"))).status());
		assertEquals(201, media.create(saleA, second.id(), MediaApiTestSupport.body(key, null, "EXTERIOR_ACCESS",
				null, 1000L, MediaApiTestSupport.sha("independent"))).status());
	}

	// ==================== SM-24 ====================

	@Test
	void unknownOrMissingKey_400() throws Exception {
		Draft draft = draft(saleA);
		Map<String, Object> unknown = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("unknown-key"));
		unknown.put("fileName", "IMG_0001.jpg");
		Map<String, Object> missing = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("missing-key"));
		missing.remove("caption");

		assertError(media.create(saleA, draft.id(), unknown), 400, "VALIDATION_FAILED", "fileName");
		Result missingCaption = media.create(saleA, draft.id(), missing);
		assertError(missingCaption, 400, "VALIDATION_FAILED", "caption");
		assertEquals("caption is required.", missingCaption.body().path("errors").path(0).path("message").asString());
		assertEquals(List.of(), media.photos(draft.id()));
	}

	@Test
	void malformedValues_400OnTheirPaths() throws Exception {
		Draft draft = draft(saleA);

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body("not-a-ulid", null, "EXTERIOR_ACCESS",
				null, 1000L, MediaApiTestSupport.sha("bad-key"))), 400, "VALIDATION_FAILED", "uploadKey");
		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"SELFIE", null, 1000L, MediaApiTestSupport.sha("bad-category"))), 400, "VALIDATION_FAILED",
				"category");
		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", null, 1000L, "abc")), 400, "VALIDATION_FAILED", "contentSha256");
		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", "x".repeat(201), 1000L, MediaApiTestSupport.sha("long-caption"))), 400,
				"VALIDATION_FAILED", "caption");
		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", null, 0L, MediaApiTestSupport.sha("empty"))), 400, "VALIDATION_FAILED",
				"sizeBytes");
	}

	@Test
	void phoneInCaption_400() throws Exception {
		Draft draft = draft(saleA);

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"EXTERIOR_ACCESS", "Gọi chủ nhà 0912 345 678", 1000L, MediaApiTestSupport.sha("phone"))), 400,
				"VALIDATION_FAILED", "caption");
	}

	@Test
	void sharedCategoryOutsideParts_400() throws Exception {
		Draft draft = draft(saleA);
		checklists.saved(saleA, draft.id(), "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(SPACE, "P101"))));

		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), null,
				"SHARED_KITCHEN", null, 1000L, MediaApiTestSupport.sha("shared"))), 400, "VALIDATION_FAILED",
				"category");
		assertError(media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(),
				OTHER_SPACE, "MAIN_SPACE", null, 1000L, MediaApiTestSupport.sha("not-in-g1"))), 400,
				"VALIDATION_FAILED", "spaceId");
		Result inSpace = media.create(saleA, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(), SPACE,
				"MAIN_SPACE", "Phòng chính", 1000L, MediaApiTestSupport.sha("in-g1")));
		assertEquals(201, inSpace.status(), inSpace.rawBody());
		assertEquals("P101", inSpace.body().path("media").path("spaceCodeAtUpload").asString());
		assertEquals("Phòng chính", inSpace.body().path("media").path("caption").asString());
	}

	@Test
	void body17KiB_413() throws Exception {
		Draft draft = draft(saleA);
		Map<String, Object> body = MediaApiTestSupport.exterior(MediaApiTestSupport.sha("big"));
		body.put("caption", "x".repeat(17 * 1024));

		assertError(media.create(saleA, draft.id(), body), 413, "PAYLOAD_TOO_LARGE", null);
	}

	// ==================== helpers ====================

	private static final String SPACE = "01JMEDIASPACE0000000000001";
	private static final String OTHER_SPACE = "01JMEDIASPACE0000000000002";

	private record Draft(Visit visit, String id) {
	}

	/** A visited lead of the sale and its started checklist. */
	private Draft draft(TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private static JsonNode strip(JsonNode body) {
		return ((tools.jackson.databind.node.ObjectNode) body.deepCopy()).without("correlationId");
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
