package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

/**
 * The ADMIN security hold and its release (survey-media spec A-23, A-29, 4.3, 5.4; SM-30; plan Task A20): who may
 * hold, what a hold changes for every viewer, the one event per change, removal and cascade while held, and the
 * release asking for the deletion of a removed photo. Needs Docker.
 */
class MediaHoldApiPostgresTest extends MediaApiTestBase {

	private static final String MEDIA = MediaApiTestSupport.MEDIA;

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

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser saleB;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;
	private TestUser superAdmin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0990");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Hold Sale", "SALE");
		saleB = survey.user("Hold Sale B", "SALE");
		leader = survey.user("Hold Leader", "SALE_LEADER");
		manager = survey.user("Hold Manager", "SALES_MANAGER");
		admin = survey.user("Hold Admin", "ADMIN");
		superAdmin = survey.user("Hold Super Admin", "SUPER_ADMIN");
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

	@Test
	void adminAndSuperAdmin_holdOnEditableReadOnlyAndClosedLead() throws Exception {
		Draft editable = draft();
		Draft readOnly = draft();
		SurveyApiTestSupport.ok(survey.moveTo(leader, readOnly.visit().leadId(), "ON_HOLD"));
		Draft closed = draft();
		Map<String, Object> reject = new LinkedHashMap<>();
		reject.put("toStatus", "REJECTED");
		reject.put("expectedVersion", survey.version(closed.visit().leadId()));
		reject.put("reason", "Not a fit");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/"
				+ closed.visit().leadId() + "/status-changes", reject));

		for (Draft draft : List.of(editable, readOnly, closed)) {
			for (TestUser caller : List.of(admin, superAdmin)) {
				String id = photo(draft, Row.READY);
				JsonNode answer = ok(hold(caller, id, "SECURITY_INCIDENT"));
				assertEquals(caller.userId(), answer.path("media").path("held").path("byUserId").asString());
				assertEquals(media.setVersion(draft.id()), answer.path("setVersion").asLong());
			}
		}
	}

	@Test
	void abusiveContentOrOther_400() throws Exception {
		String id = photo(draft(), Row.READY);

		for (String reason : List.of("ABUSIVE_CONTENT", "OTHER")) {
			assertEquals(400, hold(admin, id, reason).status(), reason);
		}
		assertEquals(400, checklists.api().post(admin, MEDIA + "/" + id + "/hold",
				Map.of("reason", "SECURITY_INCIDENT", "note", "x")).status(), "unknown key");
		assertEquals(400, checklists.api().post(admin, MEDIA + "/" + id + "/hold-release",
				Map.of("note", "x")).status(), "unknown key");
		assertNull(media.row(id).get("held_at"));
	}

	@Test
	void nonAdminReads409AtOnce() throws Exception {
		Draft draft = draft();
		String id = readyWithVariants(draft, 11);
		assertEquals(200, media.read(sale, id, "thumbnail").status());

		ok(hold(admin, id, "SECURITY_INCIDENT"));

		assertEquals(409, media.read(sale, id, "thumbnail").status());
		assertEquals(409, media.read(leader, id, "preview").status());
	}

	@Test
	void listShowsHeld_saleOnlyAtCaptionHidden_adminFull() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		jdbc.update("update survey_media set caption = 'Cửa sau' where id = ?", id);
		ok(hold(admin, id, "CREDENTIAL_EXPOSURE"));

		JsonNode forSale = item(ok(media.list(sale, draft.id(), null)), id);
		JsonNode forAdmin = item(ok(media.list(admin, draft.id(), null)), id);

		assertFalse(forSale.path("held").path("at").isNull());
		assertTrue(forSale.path("held").path("byUserId").isNull());
		assertTrue(forSale.path("held").path("reason").isNull());
		assertTrue(forSale.path("caption").isNull());
		assertTrue(forSale.path("captionHidden").asBoolean());
		assertEquals(admin.userId(), forAdmin.path("held").path("byUserId").asString());
		assertEquals("CREDENTIAL_EXPOSURE", forAdmin.path("held").path("reason").asString());
		assertEquals("Cửa sau", forAdmin.path("caption").asString());
	}

	@Test
	void coverageMissing() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.READY, minutesAgo(5), sha(), null, null,
				"EXTERIOR_ACCESS");
		ok(hold(admin, id, "SECURITY_INCIDENT"));

		JsonNode coverage = ok(media.list(sale, draft.id(), null)).path("coverage");
		for (JsonNode pair : coverage) {
			if ("EXTERIOR_ACCESS".equals(pair.path("category").asString())) {
				assertEquals("MISSING", pair.path("state").asString());
				assertEquals(0, pair.path("readyCount").asInt());
			}
		}
	}

	@Test
	void oneHeldEventInTransaction_noHistory_noDraftChange() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		long checklistVersion = checklists.checklistVersion(draft.id());
		int activities = jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?",
				Integer.class, draft.visit().leadId());

		ok(hold(admin, id, "SECURITY_INCIDENT"));

		List<Map<String, Object>> events = holdEvents(id);
		assertEquals(1, events.size());
		assertEquals("HOLD", events.get(0).get("action"));
		assertEquals("HELD", events.get(0).get("outcome"));
		assertEquals(admin.userId(), events.get(0).get("user_id"));
		assertEquals(activities, jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?",
				Integer.class, draft.visit().leadId()));
		assertEquals(checklistVersion, checklists.checklistVersion(draft.id()));
		assertEquals("READY", media.row(id).get("status"));
	}

	@Test
	void adminReadsPreviewAndFull_mediaHeldRead() throws Exception {
		Draft draft = draft();
		String id = readyWithVariants(draft, 21);
		ok(hold(admin, id, "SECURITY_INCIDENT"));

		assertEquals(200, media.read(admin, id, "preview").status());
		assertEquals(200, media.read(admin, id, "full").status());

		assertEquals(2, jdbc.queryForObject("select count(*) from survey_access_logs where resource_id = ? and"
				+ " resource_type = 'MEDIA_HELD_READ'", Integer.class, id));
	}

	@Test
	void holderRemovalAndG1Cascade_goThroughNoDeletionRequest_adminStillReads_releaseRequestsDeletion()
			throws Exception {
		Draft draft = draft();
		String id = readyWithVariants(draft, 31);
		ok(hold(admin, id, "SECURITY_INCIDENT"));

		ok(media.remove(sale, id, null));
		assertNull(media.row(id).get("content_delete_requested_at"));
		assertEquals(0, media.jobs(id).size());
		assertEquals(200, media.read(admin, id, "full").status(), "kept for the investigation");

		Instant released = Instant.now();
		ok(release(admin, id));

		assertNotNull(media.row(id).get("content_delete_requested_at"));
		Map<String, Object> job = media.jobs(id).get(0);
		assertEquals("DELETE_CONTENT", job.get("kind"));
		assertTrue(((Timestamp) job.get("due_at")).toInstant().isAfter(released.plus(Duration.ofMinutes(9))));
	}

	@Test
	void credentialExposure_captionHiddenAfterRelease() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		jdbc.update("update survey_media set caption = 'Mật khẩu wifi' where id = ?", id);
		ok(hold(admin, id, "CREDENTIAL_EXPOSURE"));

		ok(release(admin, id));

		JsonNode forSale = item(ok(media.list(sale, draft.id(), null)), id);
		assertTrue(forSale.path("held").isNull(), "a released hold is not returned");
		assertTrue(forSale.path("caption").isNull());
		assertTrue(forSale.path("captionHidden").asBoolean());
	}

	@Test
	void heldProcessingEndingRejectedOrFailed_staysHeld_listedRemovable() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.PROCESSING);
		ok(hold(admin, id, "SECURITY_INCIDENT"));
		jdbc.update("update survey_media set status = 'FAILED', status_reason = 'PROCESSING_UNAVAILABLE',"
				+ " finished_at = ? where id = ?", Timestamp.from(Instant.now()), id);

		JsonNode list = ok(media.list(sale, draft.id(), null));
		JsonNode listed = null;
		for (JsonNode candidate : list.path("notAcceptedItems")) {
			if (id.equals(candidate.path("id").asString())) {
				listed = candidate;
			}
		}
		assertNotNull(listed, list.toString());
		assertFalse(listed.path("held").path("at").isNull());
		ok(media.remove(sale, id, null));
	}

	@Test
	void secondHold_noOpNoBumpNoEventFirstReasonKept() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		ok(hold(admin, id, "SECURITY_INCIDENT"));
		long version = media.setVersion(draft.id());

		JsonNode again = ok(hold(superAdmin, id, "CREDENTIAL_EXPOSURE"));

		assertEquals(version, again.path("setVersion").asLong());
		assertEquals(version, media.setVersion(draft.id()));
		assertEquals("SECURITY_INCIDENT", media.row(id).get("hold_reason"));
		assertEquals(admin.userId(), media.row(id).get("held_by_user_id"));
		assertEquals(1, holdEvents(id).size());
	}

	@Test
	void holdReleaseHoldInOneMinute_twoHoldOneReleaseEvent() throws Exception {
		String id = photo(draft(), Row.READY);

		ok(hold(admin, id, "SECURITY_INCIDENT"));
		ok(release(admin, id));
		ok(hold(admin, id, "SECURITY_INCIDENT"));

		List<String> kinds = holdEvents(id).stream().map(event -> event.get("action") + "/" + event.get("outcome"))
				.sorted().toList();
		assertEquals(List.of("HOLD/HELD", "HOLD/HELD", "RELEASE/RELEASED"), kinds);
	}

	@Test
	void holdSurvivesTakeoverAndRestart() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		ok(hold(admin, id, "SECURITY_INCIDENT"));

		checklists.reassign(leader, draft.visit().leadId(), saleB);
		assertEquals(201, checklists.takeOver(saleB, draft.id(), draft.visit()).status());

		Map<String, Object> row = media.row(id);
		assertNotNull(row.get("held_at"));
		assertNull(row.get("hold_released_at"));
		assertFalse(item(ok(media.list(saleB, draft.id(), null)), id).path("held").isNull());
	}

	@Test
	void heldProcessing_endsReadyAndHeld_aloneKeepsPairMissing() throws Exception {
		Draft draft = draft();
		String id = media.insertPhoto(draft.id(), sale.userId(), Row.PROCESSING, minutesAgo(5), sha(), null, null,
				"EXTERIOR_ACCESS");
		ok(hold(admin, id, "SECURITY_INCIDENT"));
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("update survey_media set status = 'READY', ready_at = ?, finished_at = ?, source_format = 'JPEG'"
				+ " where id = ?", now, now, id);

		JsonNode list = ok(media.list(sale, draft.id(), null));

		assertFalse(item(list, id).path("held").isNull());
		for (JsonNode pair : list.path("coverage")) {
			if ("EXTERIOR_ACCESS".equals(pair.path("category").asString())) {
				assertEquals("MISSING", pair.path("state").asString());
			}
		}
	}

	@Test
	void release_setsReleasedAtNotBeforeHeldAt_keepsHoldColumns_servableAgain() throws Exception {
		Draft draft = draft();
		String id = readyWithVariants(draft, 41);
		ok(hold(admin, id, "SECURITY_INCIDENT"));
		long version = media.setVersion(draft.id());

		JsonNode answer = ok(release(superAdmin, id));

		Map<String, Object> row = media.row(id);
		assertNotNull(row.get("held_at"));
		assertEquals("SECURITY_INCIDENT", row.get("hold_reason"));
		assertFalse(((Timestamp) row.get("hold_released_at")).before((Timestamp) row.get("held_at")));
		assertEquals(superAdmin.userId(), row.get("hold_released_by_user_id"));
		assertEquals(version + 1, answer.path("setVersion").asLong());
		assertTrue(answer.path("media").path("held").isNull());
		assertEquals(200, media.read(sale, id, "thumbnail").status(), "servable again");
	}

	@Test
	void releaseNotHeld_noOp() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		long version = media.setVersion(draft.id());

		JsonNode answer = ok(release(admin, id));

		assertEquals(version, answer.path("setVersion").asLong());
		assertEquals(0, holdEvents(id).size());
		assertNull(media.row(id).get("hold_released_at"));
	}

	@Test
	void leaderAndManager_403NoEventBeforeLookup() throws Exception {
		Draft draft = draft();
		String id = photo(draft, Row.READY);
		String missing = MediaApiTestSupport.newKey();
		int events = jdbc.queryForObject("select count(*) from survey_security_events", Integer.class);

		for (TestUser caller : List.of(leader, manager, sale)) {
			for (String target : List.of(id, missing)) {
				assertEquals(403, hold(caller, target, "SECURITY_INCIDENT").status());
				assertEquals(403, release(caller, target).status());
			}
		}

		assertEquals(events, jdbc.queryForObject("select count(*) from survey_security_events", Integer.class));
		assertNull(media.row(id).get("held_at"));
	}

	@Test
	void holdAwaitingContent_409() throws Exception {
		Draft draft = draft();
		for (Row row : List.of(Row.AWAITING_CONTENT, Row.REJECTED, Row.REMOVED_READY)) {
			Result refused = hold(admin, photo(draft, row), "SECURITY_INCIDENT");
			assertEquals(409, refused.status(), row + " " + refused.rawBody());
			assertEquals("MEDIA_NOT_AVAILABLE", refused.body().path("code").asString());
			assertEquals("status", refused.body().path("errors").path(0).path("field").asString());
		}
		assertEquals(404, hold(admin, MediaApiTestSupport.newKey(), "SECURITY_INCIDENT").status());
	}

	@Test
	void noAdminRemovalPath() throws Exception {
		String id = photo(draft(), Row.READY);

		assertEquals(403, media.remove(admin, id, null).status(), "removal stays with the holder");
		assertEquals(404, checklists.api().post(admin, MEDIA + "/" + id + "/admin-removal", Map.of()).status());
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private String photo(Draft draft, Row row) {
		return media.insertPhoto(draft.id(), sale.userId(), row, minutesAgo(5), sha());
	}

	private String readyWithVariants(Draft draft, int seed) {
		return media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, seed),
				MediaApiTestSupport.bytes(2000, seed + 1), MediaApiTestSupport.bytes(1000, seed + 2));
	}

	private Result hold(TestUser caller, String id, String reason) throws Exception {
		return checklists.api().post(caller, MEDIA + "/" + id + "/hold", Map.of("reason", reason));
	}

	private Result release(TestUser caller, String id) throws Exception {
		return checklists.api().post(caller, MEDIA + "/" + id + "/hold-release", Map.of());
	}

	private List<Map<String, Object>> holdEvents(String id) {
		return jdbc.queryForList("select user_id, action, outcome from survey_security_events where resource_id = ?"
				+ " and action in ('HOLD', 'RELEASE') order by id", id);
	}

	private static JsonNode item(JsonNode list, String id) {
		for (String part : List.of("items", "notAcceptedItems", "removedItems")) {
			for (JsonNode candidate : list.path(part)) {
				if (id.equals(candidate.path("id").asString())) {
					return candidate;
				}
			}
		}
		throw new AssertionError("no photo " + id + " in " + list);
	}

	private static Instant minutesAgo(int minutes) {
		return Instant.now().minus(Duration.ofMinutes(minutes));
	}

	private static String sha() {
		return MediaApiTestSupport.sha(MediaApiTestSupport.newKey());
	}

	private static JsonNode ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}
}
