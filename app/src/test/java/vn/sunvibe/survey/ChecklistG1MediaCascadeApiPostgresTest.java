package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.RawUpload;
import vn.sunvibe.survey.MediaApiTestSupport.Row;

/**
 * The G1 save's photo cascade (survey-media spec A-9, 4.7, 5.4, A-23; SM-25; plan Task A19): a save that removes a
 * space, or leaves the PARTS scope, removes the selected photos on the placements it ends, under the save's correction
 * for another uploader's received photo, with no History row of its own. Needs Docker.
 */
class ChecklistG1MediaCascadeApiPostgresTest extends MediaApiTestBase {

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
	private TestUser previous;
	private TestUser leader;
	private TestUser admin;
	private final ExecutorService pool = Executors.newFixedThreadPool(4);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0987");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Cascade Sale", "SALE");
		previous = survey.user("Cascade Previous Sale", "SALE");
		leader = survey.user("Cascade Leader", "SALE_LEADER");
		admin = survey.user("Cascade Admin", "ADMIN");
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
		pool.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void createRacingSpaceRemoval_bothOrders_neverPhotoOnRemovedSpace() throws Exception {
		int created = 0;
		int refused = 0;
		for (int round = 0; round < 6; round++) {
			Draft draft = parts();
			Callable<Result> create = () -> media.create(sale, draft.id(), MediaApiTestSupport.body(
					MediaApiTestSupport.newKey(), draft.space2(), "KITCHEN", null, 1000L,
					MediaApiTestSupport.sha(MediaApiTestSupport.newKey())));
			Callable<Result> save = () -> checklists.save(sale, draft.id(), "identification",
					c -> keepOnlySpace1(c, draft));
			List<Future<Result>> both = round % 2 == 0 ? List.of(pool.submit(create), pool.submit(save))
					: List.of(pool.submit(save), pool.submit(create));
			Result first = both.get(0).get(30, TimeUnit.SECONDS);
			Result second = both.get(1).get(30, TimeUnit.SECONDS);
			Result createResult = round % 2 == 0 ? first : second;
			Result saveResult = round % 2 == 0 ? second : first;

			assertEquals(200, saveResult.status(), saveResult.rawBody());
			if (createResult.status() == 201) {
				created++;
			} else {
				assertEquals(400, createResult.status(), createResult.rawBody());
				assertEquals("spaceId", createResult.body().path("errors").path(0).path("field").asString());
				refused++;
			}
			assertEquals(0, jdbc.queryForObject("select count(*) from survey_media where checklist_id = ? and"
					+ " space_id = ? and removed_at is null", Integer.class, draft.id(), draft.space2()),
					"never a selected photo on a removed space");
		}
		assertEquals(6, created + refused);
	}

	@Test
	void removingSpace_removesItsSelectedPhotos_readyDeletedProcessingCancelledReceivingCommitRefused()
			throws Exception {
		Draft draft = parts();
		String ready = media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 71),
				MediaApiTestSupport.bytes(2000, 72), MediaApiTestSupport.bytes(1000, 73));
		jdbc.update("update survey_media set space_id = ?, space_code_at_upload = 'P102', category = 'KITCHEN'"
				+ " where id = ?", draft.space2(), ready);
		String processing = photo(draft, sale, Row.PROCESSING, draft.space2(), "MAIN_SPACE");
		byte[] bytes = MediaApiTestSupport.bytes(5000, 74);
		String receiving = createdOn(draft, draft.space2(), "BATHROOM", bytes);
		String kept = photo(draft, sale, Row.READY, draft.space1(), "KITCHEN");

		try (RawUpload upload = media.openUpload(sale, receiving, bytes.length, MediaApiTestSupport.digest(bytes))) {
			upload.send(java.util.Arrays.copyOf(bytes, 2000));
			awaitStatus(receiving, "RECEIVING");

			JsonNode saved = checklists.saved(sale, draft.id(), "identification", c -> keepOnlySpace1(c, draft));
			assertNotNull(saved);

			upload.send(java.util.Arrays.copyOfRange(bytes, 2000, bytes.length));
			Map<String, String> answer = upload.answer(20_000);
			assertEquals("409", answer.get(":status"), "the in-flight commit is refused: " + answer);
		}

		for (String id : List.of(ready, processing, receiving)) {
			Map<String, Object> row = media.row(id);
			assertNotNull(row.get("removed_at"), id);
			assertEquals("CASCADE", row.get("removal_kind"), id);
			assertNotNull(row.get("removed_by_save_id"), id);
		}
		assertNotNull(media.row(ready).get("content_delete_requested_at"));
		assertEquals("DELETE_CONTENT", media.jobs(ready).get(0).get("kind"));
		assertEquals("PROCESSING", media.row(processing).get("status"), "the worker cancels it at its outcome");
		assertEquals(0, store.real().list(vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore
				.Bucket.QUARANTINE, "quarantine/" + receiving + "/", null, 10).objects().size(),
				"the refused commit deleted its object");
		assertNull(media.row(kept).get("removed_at"));
	}

	@Test
	void otherUploadersCommitted_needsCorrection_unitsInCorrectedUnitsAndCorrections() throws Exception {
		Draft draft = parts();
		String others = photo(draft, previous, Row.READY, draft.space2(), "BATHROOM");

		Result refused = checklists.save(sale, draft.id(), "identification", c -> keepOnlySpace1(c, draft));
		assertEquals(400, refused.status(), refused.rawBody());
		assertEquals("correction", refused.body().path("errors").path(0).path("field").asString());
		assertNull(media.row(others).get("removed_at"));

		JsonNode saved = checklists.saved(sale, draft.id(), "identification", ChecklistApiTestSupport.correction(
				"Phòng P102 không cho thuê", draft.visit().arrivalDate(), "Chủ nhà xác nhận"),
				c -> keepOnlySpace1(c, draft));

		JsonNode correction = saved.path("corrections").path(saved.path("corrections").size() - 1);
		List<String> units = new ArrayList<>();
		correction.path("units").forEach(unit -> units.add(unit.path("unitKey").asString() + "@"
				+ unit.path("spaceCode").asString()));
		assertTrue(units.contains("media/" + others + "@P102"), units.toString());
		String stored = jdbc.queryForObject("select corrected_units::text from survey_checklist_saves where id = ?",
				String.class, media.row(others).get("removed_by_save_id"));
		assertTrue(stored.contains("media/" + others), stored);
	}

	/**
	 * SV-244 spec AC-1: ids are opaque strings in the API (ADR-006), so a listed CASCADE removal names its G1 save by
	 * the exact decimal text of the stored id, the same text as that save's correction {@code saveId}.
	 */
	@Test
	void cascadeRemoval_listed_bySaveIdIsTheSaveIdAsAString() throws Exception {
		Draft draft = parts();
		String own = photo(draft, sale, Row.READY, draft.space2(), "KITCHEN");
		String others = photo(draft, previous, Row.READY, draft.space2(), "BATHROOM");

		JsonNode saved = checklists.saved(sale, draft.id(), "identification", ChecklistApiTestSupport.correction(
				"Phòng P102 không cho thuê", draft.visit().arrivalDate(), "Chủ nhà xác nhận"),
				c -> keepOnlySpace1(c, draft));
		String saveId = saved.path("corrections").path(saved.path("corrections").size() - 1).path("saveId")
				.asString();

		String stored = String.valueOf(((Number) media.row(others).get("removed_by_save_id")).longValue());
		assertEquals(stored, String.valueOf(((Number) media.row(own).get("removed_by_save_id")).longValue()),
				"one save removed both");

		JsonNode list = ok(media.list(sale, draft.id(), null));
		for (String id : List.of(own, others)) {
			JsonNode removed = removedEntry(list, id);
			assertEquals("CASCADE", removed.path("kind").asString(), id);
			assertTrue(removed.path("correction").isNull(), id);
			assertTrue(removed.path("bySaveId").isString(), removed.toString());
			assertEquals(stored, removed.path("bySaveId").asString(), id);
			assertEquals(saveId, removed.path("bySaveId").asString(), id);
		}
	}

	@Test
	void removedFiles_uploadAgainOnOtherSpace() throws Exception {
		Draft draft = parts();
		String sha = MediaApiTestSupport.sha(MediaApiTestSupport.newKey());
		media.insertPhoto(draft.id(), sale.userId(), Row.READY, Instant.now().minus(Duration.ofMinutes(5)), sha,
				draft.space2(), "P102", "KITCHEN");
		checklists.saved(sale, draft.id(), "identification", c -> keepOnlySpace1(c, draft));

		Result again = media.create(sale, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(),
				draft.space1(), "KITCHEN", null, 1000L, sha));

		assertEquals(201, again.status(), again.rawBody());
	}

	@Test
	void partsToWholeAndPartsToNull_removeSharedPhotos() throws Exception {
		for (String scope : java.util.Arrays.asList("WHOLE", null)) {
			Draft draft = parts();
			String sharedKitchen = photo(draft, sale, Row.READY, null, "SHARED_KITCHEN");
			String sharedBathroom = photo(draft, sale, Row.PROCESSING, null, "SHARED_BATHROOM");
			String exterior = photo(draft, sale, Row.READY, null, "EXTERIOR_ACCESS");

			checklists.saved(sale, draft.id(), "identification", c -> {
				c.put("rentalScope", scope);
				((tools.jackson.databind.node.ArrayNode) c.path("spaces")).remove(1);
			});

			assertNotNull(media.row(sharedKitchen).get("removed_at"), scope);
			assertNotNull(media.row(sharedBathroom).get("removed_at"), scope);
			assertNull(media.row(exterior).get("removed_at"), scope);
		}
	}

	@Test
	void renamedSpace_showsSpaceCodeAtUpload() throws Exception {
		Draft draft = parts();
		String id = photo(draft, sale, Row.READY, draft.space1(), "KITCHEN");

		checklists.saved(sale, draft.id(), "identification", c -> ((ObjectNode) c.path("spaces").path(0))
				.put("code", "P201"));

		JsonNode item = ok(media.list(sale, draft.id(), null)).path("items").path(0);
		assertEquals(id, item.path("id").asString());
		assertEquals("P101", item.path("spaceCodeAtUpload").asString());
		assertTrue(item.path("removed").isNull());
	}

	@Test
	void heldPhotoOnEndedPlacement_removedWithoutDeletionRequest() throws Exception {
		Draft draft = parts();
		String held = photo(draft, sale, Row.READY, draft.space2(), "KITCHEN");
		media.held(held, admin.userId(), "SECURITY_INCIDENT");

		checklists.saved(sale, draft.id(), "identification", c -> keepOnlySpace1(c, draft));

		Map<String, Object> row = media.row(held);
		assertNotNull(row.get("removed_at"));
		assertNull(row.get("content_delete_requested_at"));
		assertEquals(0, media.jobs(held).size());
	}

	@Test
	void cascade_writesNoMediaHistory() throws Exception {
		Draft draft = parts();
		photo(draft, sale, Row.READY, draft.space2(), "KITCHEN");
		int saved = checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size();

		checklists.saved(sale, draft.id(), "identification", c -> keepOnlySpace1(c, draft));

		assertEquals(0, checklists.activities(draft.visit().leadId(), "CHECKLIST_MEDIA_REMOVED").size());
		assertEquals(saved + 1, checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size());
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id, String space1, String space2) {
	}

	/** A draft whose G1 is PARTS with the spaces P101 and P102. */
	private Draft parts() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String space1 = checklists.newId();
		String space2 = checklists.newId();
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(space1, "P101"), checklists.space(space2, "P102"))));
		return new Draft(visit, id, space1, space2);
	}

	private static void keepOnlySpace1(ObjectNode g1, Draft draft) {
		tools.jackson.databind.node.ArrayNode spaces = (tools.jackson.databind.node.ArrayNode) g1.path("spaces");
		for (int i = spaces.size() - 1; i >= 0; i--) {
			if (draft.space2().equals(spaces.path(i).path("spaceId").asString())) {
				spaces.remove(i);
			}
		}
	}

	private String photo(Draft draft, TestUser uploader, Row row, String spaceIdOrNull, String category) {
		String code = spaceIdOrNull == null ? null : spaceIdOrNull.equals(draft.space1()) ? "P101" : "P102";
		return media.insertPhoto(draft.id(), uploader.userId(), row, Instant.now().minus(Duration.ofMinutes(5)),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()), spaceIdOrNull, code, category);
	}

	private String createdOn(Draft draft, String spaceId, String category, byte[] bytes) throws Exception {
		Result created = media.create(sale, draft.id(), MediaApiTestSupport.body(MediaApiTestSupport.newKey(),
				spaceId, category, null, (long) bytes.length, MediaApiTestSupport.sha(bytes)));
		assertEquals(201, created.status(), created.rawBody());
		return created.body().path("media").path("id").asString();
	}

	private void awaitStatus(String id, String status) throws InterruptedException {
		long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!status.equals(media.row(id).get("status")) && System.nanoTime() < until) {
			Thread.sleep(50);
		}
		assertEquals(status, media.row(id).get("status"));
	}

	/** The {@code removed} of the photo's entry in the list's {@code removedItems}. */
	private static JsonNode removedEntry(JsonNode list, String id) {
		for (JsonNode item : list.path("removedItems")) {
			if (id.equals(item.path("id").asString())) {
				return item.path("removed");
			}
		}
		throw new AssertionError(id + " not in removedItems: " + list);
	}

	private static JsonNode ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}
}
