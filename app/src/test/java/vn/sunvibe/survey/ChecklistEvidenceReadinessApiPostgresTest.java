package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * Evidence readiness and the media block of the checklist read and the survey block (survey-media spec 4.6, 5.4, 7.1,
 * A-1; SM-10; plan Task A18): one item per required pair that is not covered, missingCount equal to the items in each
 * reader, one REPEATABLE READ snapshot of the groups, the set row and the photos even while a photo commits, and the
 * same uploadEnabled as the media list. Needs Docker.
 */
class ChecklistEvidenceReadinessApiPostgresTest extends MediaApiTestBase {

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
	private MediaTestSetReads setReads;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;
	private TestUser manager;
	private final ExecutorService committer = Executors.newSingleThreadExecutor();

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0984");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Evidence Sale", "SALE");
		leader = survey.user("Evidence Leader", "SALE_LEADER");
		manager = survey.user("Evidence Manager", "SALES_MANAGER");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void reset() {
		switches.reset();
		setReads.reset();
	}

	@AfterAll
	void tearDown() {
		committer.shutdownNow();
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void missingCountOfReadAndSurveyBlock_equalsMissingItems() throws Exception {
		Draft draft = whole();
		photo(draft, "EXTERIOR_ACCESS", null, Row.READY);
		photo(draft, "KITCHEN", draft.spaceId(), Row.PROCESSING);

		JsonNode read = read(draft);

		assertEquals(List.of(draft.spaceId() + "/MAIN_SPACE/EVIDENCE_MISSING",
				draft.spaceId() + "/KITCHEN/EVIDENCE_PROCESSING", draft.spaceId() + "/BATHROOM/EVIDENCE_MISSING"),
				evidence(read));
		assertEquals("SUBMISSION_NOT_AVAILABLE", read.path("readiness").path("submissionUnavailableReason").asString());
		assertFalse(read.path("readiness").path("submissionAvailable").asBoolean());
		assertTrue(read.path("media").path("available").asBoolean());
		assertEquals(media.setVersion(draft.id()), read.path("media").path("setVersion").asLong());
		assertEquals(read.path("readiness").path("missing").size(), summaryCount(draft));
	}

	@Test
	void g1OrG4Edit_recomputesCoverage() throws Exception {
		Draft draft = draft();
		assertEquals(List.of("asset/EXTERIOR_ACCESS/EVIDENCE_MISSING"), evidence(read(draft)),
				"no scope and no spaces: only the exterior is evaluated");

		String spaceId = checklists.newId();
		checklists.saved(sale, draft.id(), "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "A1"))));
		assertEquals(List.of("asset/EXTERIOR_ACCESS/EVIDENCE_MISSING", spaceId + "/MAIN_SPACE/EVIDENCE_MISSING",
				spaceId + "/KITCHEN/EVIDENCE_MISSING", spaceId + "/BATHROOM/EVIDENCE_MISSING"), evidence(read(draft)));

		JsonNode afterG4 = checklists.saved(sale, draft.id(), "condition", c -> ChecklistApiTestSupport.set(c,
				"spaces.0.kitchen", checklists.notApplicable("Không có bếp", false)));
		List<String> expected = List.of("asset/EXTERIOR_ACCESS/EVIDENCE_MISSING",
				spaceId + "/MAIN_SPACE/EVIDENCE_MISSING", spaceId + "/BATHROOM/EVIDENCE_MISSING");
		assertEquals(expected, evidence(afterG4), "the save's answer");
		assertEquals(expected, evidence(read(draft)));
		assertEquals(read(draft).path("readiness").path("missing").size(),
				summaryCount(new Draft(draft.visit(), draft.id(), spaceId)));
	}

	@Test
	void photoCommitsConcurrently_countMatchesItemsInOneSnapshot() throws Exception {
		Draft draft = whole();
		photo(draft, "DETAIL", null, Row.READY);
		long before = media.setVersion(draft.id());
		setReads.afterNextRead(draft.id(), () -> commitAside(draft, "EXTERIOR_ACCESS", null));

		JsonNode during = read(draft);

		assertEquals(before, during.path("media").path("setVersion").asLong(), "the set version of the snapshot");
		assertTrue(evidence(during).contains("asset/EXTERIOR_ACCESS/EVIDENCE_MISSING"),
				"the photo committed after the snapshot is not seen: " + evidence(during));
		JsonNode after = read(draft);
		assertEquals(before + 1, after.path("media").path("setVersion").asLong());
		assertFalse(evidence(after).contains("asset/EXTERIOR_ACCESS/EVIDENCE_MISSING"));

		int count = after.path("readiness").path("missing").size();
		setReads.afterNextRead(draft.id(), () -> commitAside(draft, "MAIN_SPACE", draft.spaceId()));
		assertEquals(count, summaryCount(draft), "the block's count of its own snapshot");
		assertEquals(count - 1, summaryCount(draft));
		assertEquals(count - 1, read(draft).path("readiness").path("missing").size());
	}

	@Test
	void mediaObject_uploadEnabledEqualsListUploadEnabled() throws Exception {
		Draft draft = whole();
		photo(draft, "DETAIL", null, Row.READY);

		assertUploadEnabled(draft, sale, true);
		assertUploadEnabled(draft, manager, false);

		MediaIntakeFixture.pause(jdbc, PauseReason.STORE);
		assertUploadEnabled(draft, sale, false);
		MediaIntakeFixture.open(jdbc);

		switches.uploads(false);
		assertUploadEnabled(draft, sale, false);
		assertTrue(read(draft).path("media").path("available").asBoolean(), "a set row keeps photos available");
		Draft without = draft();
		JsonNode none = read(without);
		assertFalse(none.path("media").path("available").asBoolean());
		JsonNode missing = none.path("readiness").path("missing");
		assertEquals("EVIDENCE_PENDING", missing.path(missing.size() - 1).path("problem").asString());
		assertEquals("EVIDENCE_NOT_AVAILABLE", none.path("readiness").path("submissionUnavailableReason").asString());
	}

	@Test
	void readTransaction_isRepeatableReadAndStillWritesAccessLog() throws Exception {
		Draft draft = whole();
		photo(draft, "DETAIL", null, Row.READY);
		AtomicReference<List<String>> seen = new AtomicReference<>();
		int rows = accessRows(draft, "CHECKLIST");

		setReads.afterNextRead(draft.id(), () -> seen.set(transactionOfThisThread()));
		read(draft);

		assertEquals(List.of("repeatable read", "off"), seen.get(), "isolation and read-only of the checklist read");
		assertEquals(rows + 1, accessRows(draft, "CHECKLIST"));

		int blockRows = accessRows(draft, "SURVEY_BLOCK");
		seen.set(null);
		setReads.afterNextRead(draft.id(), () -> seen.set(transactionOfThisThread()));
		summaryCount(draft);

		assertEquals(List.of("repeatable read", "off"), seen.get(), "isolation and read-only of the survey block");
		assertEquals(blockRows + 1, accessRows(draft, "SURVEY_BLOCK"));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id, String spaceId) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()), null);
	}

	/** A draft whose G1 is WHOLE with one observed space (code A1). */
	private Draft whole() throws Exception {
		Draft draft = draft();
		String spaceId = checklists.newId();
		checklists.saved(sale, draft.id(), "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "A1"))));
		return new Draft(draft.visit(), draft.id(), spaceId);
	}

	private String photo(Draft draft, String category, String spaceIdOrNull, Row row) {
		return media.insertPhoto(draft.id(), sale.userId(), row, Instant.now().minus(Duration.ofMinutes(5)),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()), spaceIdOrNull, spaceIdOrNull == null ? null
						: "A1", category);
	}

	/** A usable photo committed by another connection while the read waits (spec 5.4: a concurrent commit). */
	private void commitAside(Draft draft, String category, String spaceIdOrNull) {
		try {
			committer.submit(() -> photo(draft, category, spaceIdOrNull, Row.READY)).get(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Runs on the reading thread, inside its transaction: what PostgreSQL says about that transaction. */
	private List<String> transactionOfThisThread() {
		return List.of(jdbc.queryForObject("SHOW transaction_isolation", String.class),
				jdbc.queryForObject("SHOW transaction_read_only", String.class));
	}

	private JsonNode read(Draft draft) throws Exception {
		return checklists.readOk(sale, draft.id()).body();
	}

	private int summaryCount(Draft draft) throws Exception {
		Result block = survey.api().get(sale, SurveyApiTestSupport.LEADS + "/" + draft.visit().leadId() + "/survey");
		assertEquals(200, block.status(), block.rawBody());
		return SurveyApiTestSupport.appointmentOf(block.body(), draft.visit().appointmentId()).path("checklists")
				.path(0).path("missingCount").asInt();
	}

	/** The evidence items as {@code placement/category/problem}. */
	private static List<String> evidence(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		for (JsonNode item : checklist.path("readiness").path("missing")) {
			if ("EVIDENCE".equals(item.path("group").asString())) {
				items.add((item.path("spaceId").isNull() ? "asset" : item.path("spaceId").asString()) + "/"
						+ item.path("field").asString() + "/" + item.path("problem").asString());
			}
		}
		return items;
	}

	private void assertUploadEnabled(Draft draft, TestUser viewer, boolean expected) throws Exception {
		JsonNode read = checklists.readOk(viewer, draft.id()).body();
		Result list = media.list(viewer, draft.id(), null);
		assertEquals(200, list.status(), list.rawBody());
		assertEquals(expected, read.path("media").path("uploadEnabled").asBoolean(), viewer.userId());
		assertEquals(list.body().path("uploadEnabled").asBoolean(), read.path("media").path("uploadEnabled")
				.asBoolean(), "the same function as the list");
	}

	private int accessRows(Draft draft, String type) {
		String resource = "SURVEY_BLOCK".equals(type) ? draft.visit().leadId() : draft.id();
		return jdbc.queryForObject("select count(*) from survey_access_logs where resource_type = ? and"
				+ " resource_id = ?", Integer.class, type, resource);
	}
}
