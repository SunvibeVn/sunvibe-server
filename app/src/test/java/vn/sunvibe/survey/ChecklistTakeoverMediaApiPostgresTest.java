package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.RawUpload;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;

/**
 * The takeover and the photos (survey-media spec 4.8, A-22, 5.4; SM-8; plan Task A19): the previous holder's open
 * uploads end in the takeover's transaction, everything else stays, and the checkpoint stays schema 2. Needs Docker.
 */
class ChecklistTakeoverMediaApiPostgresTest extends MediaApiTestBase {

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
	private TestUser saleA;
	private TestUser saleB;
	private TestUser leader;
	private final ExecutorService pool = Executors.newFixedThreadPool(4);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0988");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		saleA = survey.user("Takeover Sale A", "SALE");
		saleB = survey.user("Takeover Sale B", "SALE");
		leader = survey.user("Takeover Leader", "SALE_LEADER");
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
	void previousHolderOpenUploads_expireAtTakeover_inflightCommitRefused() throws Exception {
		Draft draft = draft();
		String awaiting = media.created(saleA, draft.id(), MediaApiTestSupport.bytes(1500, 81));
		byte[] bytes = MediaApiTestSupport.bytes(5000, 82);
		String receiving = media.created(saleA, draft.id(), bytes);

		try (RawUpload upload = media.openUpload(saleA, receiving, bytes.length, MediaApiTestSupport.digest(bytes))) {
			upload.send(Arrays.copyOf(bytes, 2000));
			awaitStatus(receiving, "RECEIVING");

			handOver(draft);

			upload.send(Arrays.copyOfRange(bytes, 2000, bytes.length));
			Map<String, String> answer = upload.answer(20_000);
			assertTrue(answer.get(":status").startsWith("4"), "the in-flight commit is refused: " + answer);
		}

		assertEquals("UPLOAD_EXPIRED", media.row(awaiting).get("status"));
		assertEquals("UPLOAD_EXPIRED", media.row(receiving).get("status"));
		assertEquals(0, store.real().list(MediaObjectStore.Bucket.QUARANTINE, "quarantine/" + receiving + "/", null,
				10).objects().size(), "the refused commit deleted its object");
	}

	@Test
	void newHolderUploadsSameFile_accepted() throws Exception {
		Draft draft = draft();
		byte[] bytes = MediaApiTestSupport.bytes(1500, 83);
		media.created(saleA, draft.id(), bytes);
		handOver(draft);

		Result again = media.create(saleB, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(bytes)));

		assertEquals(201, again.status(), again.rawBody());
		assertEquals(saleB.userId(), again.body().path("media").path("uploadedByUserId").asString());
	}

	@Test
	void processingOfPrevious_endsReadyWithPreviousUploader() throws Exception {
		Draft draft = draft();
		String processing = photo(draft, saleA, Row.PROCESSING);

		handOver(draft);

		Map<String, Object> row = media.row(processing);
		assertEquals("PROCESSING", row.get("status"), "the worker finishes it into this checklist");
		assertEquals(saleA.userId(), row.get("uploaded_by_user_id"));
		assertNull(row.get("removed_at"));
	}

	@Test
	void othersUnchanged() throws Exception {
		Draft draft = draft();
		List<String> ids = List.of(photo(draft, saleA, Row.READY), photo(draft, saleA, Row.REJECTED),
				photo(draft, saleA, Row.FAILED));
		List<Map<String, Object>> before = ids.stream().map(media::row).toList();

		handOver(draft);

		assertEquals(before, ids.stream().map(media::row).toList());
	}

	@Test
	void newHolderRemovesPreviousCommitted_onlyWithCorrection() throws Exception {
		Draft draft = draft();
		String ready = photo(draft, saleA, Row.READY);
		handOver(draft);

		Result refused = media.remove(saleB, ready, null);
		assertEquals(400, refused.status(), refused.rawBody());
		assertEquals("correction", refused.body().path("errors").path(0).path("field").asString());

		Result removed = media.remove(saleB, ready, ChecklistApiTestSupport.correction("Ảnh cũ của người trước",
				draft.visit().arrivalDate(), "Chủ nhà xác nhận"));
		assertEquals(200, removed.status(), removed.rawBody());
	}

	@Test
	void twoConcurrentTakeovers_oneSuccessOne409NoDeadlock() throws Exception {
		Draft draft = draft();
		media.created(saleA, draft.id(), MediaApiTestSupport.bytes(1500, 84));
		photo(draft, saleA, Row.READY);
		checklists.reassign(leader, draft.visit().leadId(), saleB);
		long version = checklists.checklistVersion(draft.id());
		Callable<Result> takeOver = () -> checklists.takeOver(saleB, draft.id(), version, "Nhận bàn giao",
				draft.visit().arrivalDate(), "Trưởng nhóm giao");

		List<Integer> statuses = new ArrayList<>();
		for (Future<Result> future : pool.invokeAll(List.of(takeOver, takeOver), 60, TimeUnit.SECONDS)) {
			statuses.add(future.get().status());
		}

		statuses.sort(Integer::compareTo);
		assertEquals(List.of(201, 409), statuses);
	}

	@Test
	void checkpointSchema2_w16ViewUnchanged() throws Exception {
		Draft draft = draft();
		photo(draft, saleA, Row.READY);
		handOver(draft);

		Result checkpoint = checklists.checkpoint(saleB, draft.id(), 1);

		assertEquals(200, checkpoint.status(), checkpoint.rawBody());
		JsonNode body = checkpoint.body();
		assertFalse(body.has("media"));
		assertEquals(2, jdbc.queryForObject("select (snapshot::jsonb ->> 'schema')::int from"
				+ " survey_checklist_takeovers where checklist_id = ?", Integer.class, draft.id()));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		return new Draft(visit, checklists.started(saleA, visit.appointmentId()));
	}

	/** The lead goes to Sale B, who takes the draft over (asserts 201). */
	private void handOver(Draft draft) throws Exception {
		checklists.reassign(leader, draft.visit().leadId(), saleB);
		Result result = checklists.takeOver(saleB, draft.id(), draft.visit());
		assertEquals(201, result.status(), result.rawBody());
	}

	private String photo(Draft draft, TestUser uploader, Row row) {
		return media.insertPhoto(draft.id(), uploader.userId(), row, Instant.now().minus(Duration.ofMinutes(5)),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));
	}

	private void awaitStatus(String id, String status) throws InterruptedException {
		long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!status.equals(media.row(id).get("status")) && System.nanoTime() < until) {
			Thread.sleep(50);
		}
		assertEquals(status, media.row(id).get("status"));
	}
}
