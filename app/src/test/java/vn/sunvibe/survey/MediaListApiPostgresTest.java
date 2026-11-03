package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

/**
 * The media list over HTTP (survey-media spec 4.3, 7.1, 7.2, A-23, A-30; SM-26 list part; plan Task A16). Needs Docker.
 */
class MediaListApiPostgresTest extends MediaApiTestBase {

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
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0979");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("List Sale", "SALE");
		otherSale = survey.user("List Other Sale", "SALE");
		leader = survey.user("List Leader", "SALE_LEADER");
		manager = survey.user("List Manager", "SALES_MANAGER");
		admin = survey.user("List Admin", "ADMIN");
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

	@Test
	void items_notAccepted_removed_totals_coverage_limits() throws Exception {
		Draft draft = draft();
		Instant at = Instant.now().minus(Duration.ofMinutes(20));
		media.insertPhotos(draft.id(), sale.userId(), Row.READY, 2, at, 1000L, "list-ready");
		media.insertPhotos(draft.id(), sale.userId(), Row.AWAITING_CONTENT, 1, at, 1000L, "list-awaiting");
		media.insertPhotos(draft.id(), sale.userId(), Row.REJECTED, 1, at, 1000L, "list-rejected");
		media.insertPhotos(draft.id(), sale.userId(), Row.UPLOAD_EXPIRED, 1, at, 1000L, "list-expired");
		media.insertPhotos(draft.id(), sale.userId(), Row.REMOVED_READY, 1, at, 1000L, "list-removed");

		JsonNode list = ok(media.list(sale, draft.id(), null));

		assertEquals(List.of("checklistId", "setVersion", "basis", "uploadEnabled", "intakePaused", "servingEnabled",
				"canHold", "limits", "items", "notAcceptedItems", "removedItems", "totals", "coverage"),
				AcquisitionApiTestSupport.fieldNames(list));
		assertEquals(List.of(1, 2, 3), numbers(list.path("items")));
		assertEquals(List.of(5, 4), numbers(list.path("notAcceptedItems")), "newest first");
		assertEquals(List.of(6), numbers(list.path("removedItems")));
		assertEquals(2, list.path("totals").path("notAccepted").asInt());
		assertEquals(1, list.path("totals").path("removed").asInt());
		assertEquals(3, list.path("limits").path("selectedCount").asInt());
		assertEquals(1, list.path("limits").path("pendingCount").asInt());
		assertEquals(100, list.path("limits").path("maxSelected").asInt());
		assertEquals(20, list.path("limits").path("maxPending").asInt());
		assertEquals(20_971_520L, list.path("limits").path("maxFileBytes").asLong());
		JsonNode exterior = list.path("coverage").path(0);
		assertEquals("EXTERIOR_ACCESS", exterior.path("category").asString());
		assertEquals("MISSING", exterior.path("state").asString(), "DETAIL photos cover nothing");
		assertTrue(list.path("uploadEnabled").asBoolean());
		assertFalse(list.path("canHold").asBoolean());
		assertFalse(list.toString().contains("source_sha256") || list.toString().contains("quarantine/"),
				"no storage key or hash is returned");
	}

	@Test
	void knownVersionUnchanged_204() throws Exception {
		Draft draft = draft();
		JsonNode first = ok(media.list(sale, draft.id(), null));

		Result again = media.list(sale, draft.id(), MediaApiTestSupport.knownVersion(first));

		assertEquals(204, again.status(), again.rawBody());
	}

	@Test
	void leadPutOnHoldAfterPoll_oldKnownVersion200UploadEnabledFalse() throws Exception {
		Draft draft = draft();
		JsonNode first = ok(media.list(sale, draft.id(), null));
		assertTrue(first.path("uploadEnabled").asBoolean());
		SurveyApiTestSupport.ok(survey.moveTo(leader, draft.visit().leadId(), "ON_HOLD"));

		JsonNode after = ok(media.list(sale, draft.id(), MediaApiTestSupport.knownVersion(first)));

		assertFalse(after.path("uploadEnabled").asBoolean());
	}

	@Test
	void servingSwitchedOffAndOn_200EachTime() throws Exception {
		Draft draft = draft();
		JsonNode on = ok(media.list(sale, draft.id(), null));
		switches.serving(false);

		JsonNode off = ok(media.list(sale, draft.id(), MediaApiTestSupport.knownVersion(on)));
		switches.serving(true);
		JsonNode onAgain = ok(media.list(sale, draft.id(), MediaApiTestSupport.knownVersion(off)));

		assertFalse(off.path("servingEnabled").asBoolean());
		assertTrue(onAgain.path("servingEnabled").asBoolean());
	}

	@Test
	void malformedKnownVersion_400() throws Exception {
		Draft draft = draft();

		Result result = media.list(sale, draft.id(), "1.2.3");

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("VALIDATION_FAILED", result.body().path("code").asString());
		assertEquals("knownVersion", result.body().path("errors").path(0).path("field").asString());
	}

	@Test
	void heldPhoto_saleSeesOnlyHeldAtCaptionNull() throws Exception {
		Draft draft = draft();
		String id = heldReadyPhoto(draft, "SECURITY_INCIDENT", false);

		JsonNode item = item(ok(media.list(sale, draft.id(), null)), id);

		assertFalse(item.path("held").path("at").isNull());
		assertTrue(item.path("held").path("byUserId").isNull());
		assertTrue(item.path("held").path("reason").isNull());
		assertTrue(item.path("caption").isNull());
		assertTrue(item.path("captionHidden").asBoolean());
		assertFalse(item.path("contentAvailable").asBoolean());
		assertEquals("HELD", item.path("unavailableReason").asString());
	}

	@Test
	void heldPhoto_adminSeesFullHoldAndCaptionContentAvailable() throws Exception {
		Draft draft = draft();
		String id = heldReadyPhoto(draft, "SECURITY_INCIDENT", false);

		JsonNode list = ok(media.list(admin, draft.id(), null));
		JsonNode item = item(list, id);

		assertTrue(list.path("canHold").asBoolean());
		assertEquals(admin.userId(), item.path("held").path("byUserId").asString());
		assertEquals("SECURITY_INCIDENT", item.path("held").path("reason").asString());
		assertEquals("Biển số nhà", item.path("caption").asString());
		assertTrue(item.path("contentAvailable").asBoolean());
		assertTrue(item.path("unavailableReason").isNull());
	}

	@Test
	void credentialExposureReleased_captionStillHiddenForSale() throws Exception {
		Draft draft = draft();
		String id = heldReadyPhoto(draft, "CREDENTIAL_EXPOSURE", true);

		JsonNode item = item(ok(media.list(sale, draft.id(), null)), id);

		assertTrue(item.path("held").isNull(), "a released hold is not returned");
		assertTrue(item.path("caption").isNull());
		assertTrue(item.path("captionHidden").asBoolean());
		assertTrue(item.path("contentAvailable").asBoolean());
	}

	@Test
	void nonServableCases_contentAvailableFalseWithReason() throws Exception {
		Draft draft = draft();
		Instant at = Instant.now().minus(Duration.ofMinutes(20));
		String removed = media.insertPhotos(draft.id(), sale.userId(), Row.REMOVED_READY, 1, at, 1000L, "ns-removed")
				.get(0);
		String processing = media.insertPhotos(draft.id(), sale.userId(), Row.PROCESSING, 1, at, 1000L,
				"ns-processing").get(0);
		String missing = media.insertPhotos(draft.id(), sale.userId(), Row.READY, 1, at, 1000L, "ns-missing").get(0);
		jdbc.update("update survey_media set content_missing_at = ? where id = ?", Timestamp.from(Instant.now()),
				missing);

		JsonNode list = ok(media.list(sale, draft.id(), null));

		assertEquals("REMOVED", item(list, removed).path("unavailableReason").asString());
		assertEquals("NOT_READY", item(list, processing).path("unavailableReason").asString());
		assertEquals("CONTENT_MISSING", item(list, missing).path("unavailableReason").asString());
		for (String id : List.of(removed, processing, missing)) {
			assertFalse(item(list, id).path("contentAvailable").asBoolean(), id);
		}
	}

	@Test
	void managerViewer_uploadEnabledFalse() throws Exception {
		Draft draft = draft();

		JsonNode list = ok(media.list(manager, draft.id(), null));

		assertFalse(list.path("uploadEnabled").asBoolean());
		assertFalse(list.path("canHold").asBoolean());
	}

	@Test
	void anotherSale_404WithThrottledEvent() throws Exception {
		Draft draft = draft();

		Result hidden = media.list(otherSale, draft.id(), null);
		Result again = media.list(otherSale, draft.id(), null);
		Result missing = media.list(otherSale, "01JNOSUCHCHECK1ST000000000", null);

		assertEquals(404, hidden.status());
		assertEquals(404, again.status());
		assertEquals(hidden.body().path("message").asString(), missing.body().path("message").asString());
		List<Map<String, Object>> events = media.mediaEvents(draft.visit().leadId());
		assertEquals(1, events.size(), events.toString());
		assertEquals("READ", events.get(0).get("action"));
		assertEquals("NOT_VISIBLE", events.get(0).get("outcome"));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	/** A READY photo with a caption, held by the ADMIN; released afterwards when asked. */
	private String heldReadyPhoto(Draft draft, String reason, boolean released) {
		String id = media.insertPhotos(draft.id(), sale.userId(), Row.READY, 1, Instant.now().minus(
				Duration.ofMinutes(20)), 1000L, "held-" + reason + released).get(0);
		Timestamp heldAt = Timestamp.from(Instant.now().minus(Duration.ofMinutes(5)));
		jdbc.update("update survey_media set caption = 'Biển số nhà', held_at = ?, held_by_user_id = ?,"
				+ " hold_reason = ? where id = ?", heldAt, admin.userId(), reason, id);
		if (released) {
			jdbc.update("update survey_media set hold_released_at = ?, hold_released_by_user_id = ? where id = ?",
					Timestamp.from(Instant.now()), admin.userId(), id);
		}
		return id;
	}

	private static JsonNode ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	private static JsonNode item(JsonNode list, String id) {
		for (String part : List.of("items", "notAcceptedItems", "removedItems")) {
			for (JsonNode item : list.path(part)) {
				if (id.equals(item.path("id").asString())) {
					return item;
				}
			}
		}
		throw new AssertionError("no item " + id);
	}

	private static List<Integer> numbers(JsonNode items) {
		List<Integer> numbers = new ArrayList<>();
		items.forEach(item -> numbers.add(item.path("mediaNo").asInt()));
		return numbers;
	}
}
