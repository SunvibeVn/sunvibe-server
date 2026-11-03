package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.github.f4b6a3.ulid.UlidCreator;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;

/**
 * Content identity at create (survey-media spec A-16, 4.4 step 1; SM-12 create part; BR-2, BR-12, BR-20; plan Task
 * A13): the same bytes as a selected photo of the checklist are a duplicate, also for concurrent creates and when the
 * set lock is bypassed (the partial unique index); photos that do not count leave the bytes free; a committed photo of
 * another visit of the lead refuses them; another lead's photos are never compared. Needs Docker.
 */
class MediaContentIdentityApiPostgresTest extends MediaApiTestBase {

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
	private DataSource dataSource;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser leader;
	private final ExecutorService pool = Executors.newFixedThreadPool(2);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0973");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		saleA = survey.user("Identity Sale A", "SALE");
		saleB = survey.user("Identity Sale B", "SALE");
		leader = survey.user("Identity Leader", "SALE_LEADER");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void sameBytesAsSelected_409DuplicateWithMediaId() throws Exception {
		Draft draft = draft(saleA);
		String sha = MediaApiTestSupport.sha("selected-twin");
		Result first = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(sha));

		Result again = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(sha));

		assertEquals(201, first.status(), first.rawBody());
		assertConflict(again, "MEDIA_DUPLICATE_CONTENT", first.body().path("media").path("id").asString());
	}

	@Test
	void twoConcurrentCreatesSameHashDifferentKeys_one201One409() throws Exception {
		Draft draft = draft(saleA);
		String sha = MediaApiTestSupport.sha("concurrent-same-hash");

		List<Future<Result>> futures = new ArrayList<>();
		try (MediaApiTestSupport.HeldLock held = MediaApiTestSupport.holdUserLock(dataSource, jdbc,
				saleA.userId())) {
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> media.create(saleA, draft.id(), MediaApiTestSupport.exterior(sha))));
			}
			held.awaitWaiters(2);
		}
		List<Result> results = new ArrayList<>();
		for (Future<Result> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}

		Result created = results.stream().filter(r -> r.status() == 201).findFirst().orElseThrow();
		Result refused = results.stream().filter(r -> r.status() != 201).findFirst().orElseThrow();
		assertConflict(refused, "MEDIA_DUPLICATE_CONTENT", created.body().path("media").path("id").asString());
		assertEquals(1, media.photos(draft.id()).size());
	}

	@Test
	void sameHashInsertBypassingSetLock_partialUniqueBackstop409() throws Exception {
		Draft draft = draft(saleA);
		String sha = MediaApiTestSupport.sha("backstop");
		String key = MediaApiTestSupport.newKey();
		// A test seam between the check and the insert: the insert of this key first writes a selected twin with the
		// same hash, as a writer that bypassed the set lock would have; the partial unique index refuses the photo.
		jdbc.execute("CREATE OR REPLACE FUNCTION test_media_twin() RETURNS trigger AS $$ BEGIN"
				+ " IF NEW.upload_key = '" + key + "' THEN"
				+ " INSERT INTO survey_media (id, checklist_id, upload_key, media_no, version, category,"
				+ " uploaded_by_user_id, created_at, declared_size_bytes, source_sha256, status) VALUES ('"
				+ UlidCreator.getMonotonicUlid() + "', NEW.checklist_id, '" + UlidCreator.getMonotonicUlid()
				+ "', NEW.media_no + 1000, 0, 'DETAIL', NEW.uploaded_by_user_id, NEW.created_at, 1000,"
				+ " NEW.source_sha256, 'AWAITING_CONTENT'); END IF; RETURN NEW; END $$ LANGUAGE plpgsql");
		jdbc.execute("CREATE TRIGGER test_media_twin BEFORE INSERT ON survey_media FOR EACH ROW"
				+ " EXECUTE FUNCTION test_media_twin()");
		Result refused;
		try {
			refused = media.create(saleA, draft.id(), MediaApiTestSupport.body(key, null, "EXTERIOR_ACCESS", null,
					1000L, sha));
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS test_media_twin ON survey_media");
			jdbc.execute("DROP FUNCTION IF EXISTS test_media_twin()");
		}

		assertEquals(409, refused.status(), "a unique violation is mapped, never a 500: " + refused.rawBody());
		assertEquals("MEDIA_DUPLICATE_CONTENT", refused.body().path("code").asString());
		assertEquals("contentSha256", refused.body().path("errors").path(0).path("field").asString());
		assertEquals(List.of(), media.photos(draft.id()), "the twin rolled back with the refused insert");
	}

	@Test
	void sameBytesAsRemovedRejectedFailedExpired_newPhotoCallerUploader_earlierRecordUnchanged() throws Exception {
		Draft draft = draft(saleA);
		String sha = MediaApiTestSupport.sha("not-counting");
		Instant earlier = Instant.now().minus(Duration.ofHours(3));
		List<String> earlierIds = new ArrayList<>();
		for (Row row : List.of(Row.REMOVED_READY, Row.REJECTED, Row.FAILED, Row.UPLOAD_EXPIRED, Row.CANCELLED)) {
			earlierIds.add(media.insertPhoto(draft.id(), saleB.userId(), row, earlier, sha));
		}
		List<Map<String, Object>> before = media.photos(draft.id());

		Result created = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(sha));

		assertEquals(201, created.status(), created.rawBody());
		assertEquals(saleA.userId(), created.body().path("media").path("uploadedByUserId").asString());
		assertTrue(!earlierIds.contains(created.body().path("media").path("id").asString()));
		List<Map<String, Object>> after = media.photos(draft.id());
		assertEquals(before, after.subList(0, before.size()), "the earlier records are unchanged (BR-12)");
	}

	@Test
	void sameBytesAsCommittedPhotoOfEarlierChecklistAfterResurvey_409FromOtherVisit() throws Exception {
		Draft first = draft(saleA);
		String sha = MediaApiTestSupport.sha("earlier-visit");
		String earlierPhoto = media.insertPhoto(first.id(), saleA.userId(), Row.READY,
				Instant.now().minus(Duration.ofHours(1)), sha);
		String resurvey = bookResurvey(first.visit().leadId());
		survey.shiftBack(resurvey, Duration.ofDays(2));
		Instant start = jdbc.queryForObject("select start_at from survey_appointments where id = ?",
				java.sql.Timestamp.class, resurvey).toInstant();
		Result visited = checklists.recordVisit(saleA, first.visit().leadId(), resurvey, start);
		assertEquals(200, visited.status(), visited.rawBody());
		assertEquals("NEWER_VISIT", checklists.readOk(saleA, first.id()).body().path("readOnlyReason").asString());
		String second = checklists.started(saleA, resurvey);

		Result refused = media.create(saleA, second, MediaApiTestSupport.exterior(sha));

		assertConflict(refused, "MEDIA_FROM_OTHER_VISIT", earlierPhoto);
		assertEquals(201, media.create(saleA, second, MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"new-visit-photo"))).status(), "a new visit starts with no photos and takes new ones (BR-20)");
	}

	@Test
	void sameBytesOnAnotherLead_acceptedAndRevealsNothing() throws Exception {
		Draft other = draft(saleB);
		String sha = MediaApiTestSupport.sha("another-lead");
		media.insertPhoto(other.id(), saleB.userId(), Row.READY, Instant.now().minus(Duration.ofHours(1)), sha);
		Draft own = draft(saleA);

		Result created = media.create(saleA, own.id(), MediaApiTestSupport.exterior(sha));
		Result fresh = media.create(saleA, own.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"another-lead-fresh")));

		assertEquals(201, created.status(), created.rawBody());
		assertEquals(AcquisitionApiTestSupport.fieldNames(fresh.body()), AcquisitionApiTestSupport.fieldNames(
				created.body()));
		assertEquals(AcquisitionApiTestSupport.fieldNames(fresh.body().path("media")),
				AcquisitionApiTestSupport.fieldNames(created.body().path("media")));
		assertTrue(!created.rawBody().contains("allowedValues") && !created.rawBody().contains(other.id()));
		assertEquals(List.of(), media.mediaEvents(own.visit().leadId()));
		assertEquals(List.of(), media.mediaEvents(other.visit().leadId()));
	}

	@Test
	void reEncodedCopy_acceptedAsNewPhoto() throws Exception {
		Draft draft = draft(saleA);

		Result original = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"photo bytes")));
		Result reEncoded = media.create(saleA, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"photo bytes, re-encoded")));

		assertEquals(201, original.status(), original.rawBody());
		assertEquals(201, reEncoded.status(), "only identical bytes are recognized (spec 9.10): "
				+ reEncoded.rawBody());
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft(TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	/** Books a re-survey of the lead for its assignee (survey-schedule): held, contacted again, then booked. */
	private String bookResurvey(String leadId) throws Exception {
		if ("SURVEY_SCHEDULED".equals(survey.leadStatus(leadId))) {
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "ON_HOLD"));
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "CONTACTED"));
		}
		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(30));
		body.put("resurveyReason", "Owner rebuilt the kitchen");
		Result booked = survey.book(saleA, leadId, body);
		assertEquals(201, booked.status(), booked.rawBody());
		return booked.body().path("survey").path("appointments").path(0).path("id").asString();
	}

	private static void assertConflict(Result result, String code, String matchingMediaId) {
		assertEquals(409, result.status(), result.rawBody());
		assertEquals(code, result.body().path("code").asString(), result.rawBody());
		JsonNode error = result.body().path("errors").path(0);
		assertEquals("contentSha256", error.path("field").asString());
		assertEquals(1, error.path("allowedValues").size(), result.rawBody());
		assertEquals(matchingMediaId, error.path("allowedValues").path(0).asString());
	}
}
