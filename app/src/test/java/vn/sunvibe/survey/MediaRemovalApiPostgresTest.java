package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;

/**
 * The holder's removal of a photo (survey-media spec 4.7, 5.4 removal order, A-10, A-17, A-23; SM-11, SM-16; plan
 * Task A19): who may remove, when a correction is needed, the History row, the deletion request and its job, held
 * photos and the records kept. Needs Docker.
 */
class MediaRemovalApiPostgresTest extends MediaApiTestBase {

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
	private TestUser previous;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0985");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Removal Sale", "SALE");
		previous = survey.user("Removal Previous Sale", "SALE");
		leader = survey.user("Removal Leader", "SALE_LEADER");
		manager = survey.user("Removal Manager", "SALES_MANAGER");
		admin = survey.user("Removal Admin", "ADMIN");
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
	void ownPhoto_noCorrection() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.READY);
		long before = media.setVersion(draft.id());

		JsonNode answer = ok(media.remove(sale, id, null));

		JsonNode removed = answer.path("media").path("removed");
		assertEquals("HOLDER", removed.path("kind").asString());
		assertEquals(sale.userId(), removed.path("byUserId").asString());
		assertTrue(removed.path("correction").isNull());
		assertFalse(answer.path("media").path("contentAvailable").asBoolean());
		assertEquals("REMOVED", answer.path("media").path("unavailableReason").asString());
		assertEquals(before + 1, answer.path("setVersion").asLong());
		assertEquals(before + 1, media.setVersion(draft.id()));
	}

	@Test
	void otherUploadersCommitted_400WithoutCorrection() throws Exception {
		Draft draft = draft();
		String ready = photo(draft, previous, Row.READY);
		String processing = photo(draft, previous, Row.PROCESSING);

		for (String id : List.of(ready, processing)) {
			Result refused = media.remove(sale, id, null);
			assertEquals(400, refused.status(), refused.rawBody());
			assertEquals("correction", refused.body().path("errors").path(0).path("field").asString());
		}
		JsonNode answer = ok(media.remove(sale, ready, correction(draft)));

		JsonNode recorded = answer.path("media").path("removed").path("correction");
		assertEquals("Ảnh chụp nhầm phòng", recorded.path("reason").asString());
		assertEquals(draft.visit().arrivalDate().toString(), recorded.path("basis").path("confirmedOn").asString());
	}

	@Test
	void awaitingReceivingRejectedFailedCancelledExpired_free() throws Exception {
		Draft draft = draft();
		String receiving = photo(draft, previous, Row.AWAITING_CONTENT);
		media.receiving(receiving);
		for (String id : List.of(photo(draft, previous, Row.AWAITING_CONTENT), receiving,
				photo(draft, previous, Row.REJECTED), photo(draft, previous, Row.FAILED),
				photo(draft, previous, Row.CANCELLED), photo(draft, previous, Row.UPLOAD_EXPIRED))) {
			Result answer = media.remove(sale, id, null);
			assertEquals(200, answer.status(), id + " " + answer.rawBody());
			assertFalse(answer.body().path("media").path("removed").isNull(), id);
		}
		Map<String, Object> row = media.row(receiving);
		assertEquals("RECEIVING", row.get("status"), "the commit is refused later (MEDIA_NOT_AVAILABLE)");
		assertNotNull(row.get("removed_at"));
	}

	@Test
	void remove_referencedPhoto_409MediaReferenced() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.READY);
		media.evidenceRow(draft.id(), id);

		Result refused = media.remove(sale, id, null);

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("MEDIA_REFERENCED", refused.body().path("code").asString());
		assertNull(media.row(id).get("removed_at"));
	}

	@Test
	void repeat_200NoEvent() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.READY);
		ok(media.remove(sale, id, null));
		long version = media.setVersion(draft.id());
		int activities = checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_REMOVED").size();

		JsonNode again = ok(media.remove(sale, id, null));

		assertEquals(version, again.path("setVersion").asLong());
		assertEquals(version, media.setVersion(draft.id()));
		assertTrue(again.path("historyEntry").isNull());
		assertEquals(activities, checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_REMOVED").size());
		assertEquals(1, media.jobs(id).size(), "one deletion job");
	}

	@Test
	void readyRemoved_409AtOnce_deletedAfter10MinutesWithin24Hours_metadataKept() throws Exception {
		Draft draft = draft();
		String id = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 31),
				MediaApiTestSupport.bytes(2000, 32), MediaApiTestSupport.bytes(1000, 33));
		Map<String, Object> before = media.row(id);
		assertEquals(200, media.read(sale, id, "thumbnail").status());

		Instant removedAt = Instant.now();
		ok(media.remove(sale, id, null));

		assertEquals(409, media.read(sale, id, "thumbnail").status(), "not served at once");
		Map<String, Object> job = media.jobs(id).get(0);
		assertEquals("DELETE_CONTENT", job.get("kind"));
		Instant due = ((Timestamp) job.get("due_at")).toInstant();
		assertTrue(!due.isBefore(removedAt.plus(Duration.ofMinutes(10)).minusSeconds(5))
				&& due.isBefore(removedAt.plus(Duration.ofMinutes(11))), "due 10 minutes later: " + due);
		assertFalse(deletionDone(id), "nothing deleted before the due time");

		jdbc.update("update survey_media_jobs set due_at = ? where media_id = ?",
				Timestamp.from(Instant.now().minusSeconds(1)), id);
		awaitDeleted(id);

		assertEquals(0, store.real().list(MediaObjectStore.Bucket.MEDIA, "media/" + id + "/", null, 10).objects()
				.size());
		Map<String, Object> after = media.row(id);
		assertNotNull(after.get("content_deleted_at"));
		for (String kept : List.of("source_sha256", "declared_size_bytes", "uploaded_by_user_id", "media_no",
				"category", "status")) {
			assertEquals(before.get(kept), after.get(kept), kept);
		}
		assertEquals("DONE", media.jobs(id).get(0).get("state"));
		JsonNode item = ok(media.list(sale, draft.id(), null)).path("removedItems").path(0);
		assertEquals("REMOVED", item.path("unavailableReason").asString());
	}

	@Test
	void processingRemoved_cancelledNeverReady() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.PROCESSING);

		JsonNode answer = ok(media.remove(sale, id, null));

		assertEquals("PROCESSING", answer.path("media").path("status").asString(),
				"the worker ends it CANCELLED REMOVED at its outcome");
		assertNull(media.row(id).get("content_delete_requested_at"));
		assertEquals(0, media.jobs(id).size(), "no deletion job: nothing was served");
		assertFalse(answer.path("historyEntry").isNull(), "a committed photo");
	}

	@Test
	void readOnly_409() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.READY);
		SurveyApiTestSupport.ok(survey.moveTo(leader, draft.visit().leadId(), "ON_HOLD"));

		Result refused = media.remove(sale, id, null);

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_READ_ONLY", refused.body().path("code").asString());
	}

	@Test
	void heldReady_removedNoRequestNoJob_contentKeptUntilRelease() throws Exception {
		Draft draft = draft();
		String id = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 41),
				MediaApiTestSupport.bytes(2000, 42), MediaApiTestSupport.bytes(1000, 43));
		media.held(id, admin.userId(), "SECURITY_INCIDENT");

		JsonNode answer = ok(media.remove(sale, id, null));

		assertFalse(answer.path("media").path("removed").isNull());
		Map<String, Object> row = media.row(id);
		assertNull(row.get("content_delete_requested_at"));
		assertEquals(0, media.jobs(id).size());
		assertEquals(3, store.real().list(MediaObjectStore.Bucket.MEDIA, "media/" + id + "/", null, 10).objects()
				.size(), "the content is kept for the investigation");
	}

	@Test
	void records_keepReasonSizeHashIdsCaption() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.REJECTED);
		jdbc.update("update survey_media set caption = 'Cửa chính' where id = ?", id);
		Map<String, Object> before = media.row(id);

		ok(media.remove(sale, id, null));

		Map<String, Object> after = media.row(id);
		for (String kept : List.of("status_reason", "declared_size_bytes", "source_sha256", "id", "checklist_id",
				"uploaded_by_user_id", "caption")) {
			assertEquals(before.get(kept), after.get(kept), kept);
		}
	}

	@Test
	void correctionTextWithPhone_400() throws Exception {
		Draft draft = draft();
		String id = photo(draft, previous, Row.READY);

		Result reason = media.remove(sale, id, ChecklistApiTestSupport.correction("Gọi 0905123456 để hỏi",
				draft.visit().arrivalDate(), "Chủ nhà xác nhận"));
		Result text = media.remove(sale, id, ChecklistApiTestSupport.correction("Ảnh sai",
				draft.visit().arrivalDate(), "Theo số 0905 123 456"));

		assertEquals(400, reason.status(), reason.rawBody());
		assertEquals("correction.reason", reason.body().path("errors").path(0).path("field").asString());
		assertEquals(400, text.status(), text.rawBody());
		assertEquals("correction.basis.text", text.body().path("errors").path(0).path("field").asString());
		assertNull(media.row(id).get("removed_at"));
	}

	@Test
	void removalOfCommitted_writesMediaRemovedHistory() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.READY);

		JsonNode answer = ok(media.remove(sale, id, null));

		JsonNode entry = answer.path("historyEntry");
		assertEquals("CHECKLIST_MEDIA_REMOVED", entry.path("type").asString());
		assertEquals("DETAIL", entry.path("referenceDetail").asString());
		assertEquals(1, checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_REMOVED").size());
	}

	@Test
	void tidyingRemoval_writesNoHistory() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.FAILED);

		JsonNode answer = ok(media.remove(sale, id, null));

		assertTrue(answer.path("historyEntry").isNull());
		assertEquals(0, checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_REMOVED").size());
	}

	@Test
	void adminLeaderManagerNotHolder_403() throws Exception {
		Draft draft = draft();
		String id = photo(draft, sale, Row.READY);

		for (TestUser caller : List.of(admin, leader, manager)) {
			Result refused = media.remove(caller, id, null);
			assertEquals(403, refused.status(), caller.userId() + " " + refused.rawBody());
		}
		assertNull(media.row(id).get("removed_at"));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private String photo(Draft draft, TestUser uploader, Row row) {
		return media.insertPhoto(draft.id(), uploader.userId(), row, Instant.now().minus(Duration.ofMinutes(5)),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));
	}

	private static Map<String, Object> correction(Draft draft) {
		LocalDate on = draft.visit().arrivalDate();
		return ChecklistApiTestSupport.correction("Ảnh chụp nhầm phòng", on, "Chủ nhà xác nhận qua điện thoại");
	}

	private boolean deletionDone(String id) {
		return media.row(id).get("content_deleted_at") != null;
	}

	/** The deletion job runs on the media scheduler too: run it here and wait for whichever run takes it. */
	private void awaitDeleted(String id) throws InterruptedException {
		long until = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while (!deletionDone(id) && System.nanoTime() < until) {
			deletion.runOnce();
			Thread.sleep(100);
		}
		assertTrue(deletionDone(id), "deleted once due");
	}

	private static JsonNode ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}
}
