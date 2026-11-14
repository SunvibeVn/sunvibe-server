package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
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
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;

/**
 * survey-checklist SV-211: a photo that a reviewer of the visit marked not accepted stays out of the next revision of
 * that visit (SV-211 spec RC-1 to RC-5). The earlier revision keeps it with its mark, a pair it alone covered is missing
 * until a new photo is uploaded on it, a revision started before the fix does not pass it on, and its bytes may be
 * uploaded again as a new photo. Needs Docker.
 */
class ChecklistRevisionRejectedPhotoApiPostgresTest extends MediaApiTestBase {

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
	private MediaTestSwitches switches;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private ChecklistReviewApiSupport review;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0955");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Rejected Sale", "SALE");
		leader = survey.user("Rejected Leader", "SALE_LEADER");
	}

	@BeforeEach
	void open() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void reset() {
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	private Result revise(TestUser caller, String checklistId, Map<String, Object> intakeOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", intakeOrNull);
		return checklists.api().post(caller, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId + "/revisions",
				body);
	}

	private String revised(TestUser caller, String checklistId, Map<String, Object> intakeOrNull) throws Exception {
		Result answer = revise(caller, checklistId, intakeOrNull);
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("checklist").path("id").asString();
	}

	private List<String> selected(String checklistId) {
		return jdbc.queryForList("select media_id from survey_checklist_media_selections where checklist_id = ?"
				+ " and deselected_at is null order by media_id", String.class, checklistId);
	}

	/** The leader returns the revision for documents and marks each photo not accepted; the answer must be 200. */
	private void returnedWithMarks(String checklistId, String... rejectedMediaIds) throws Exception {
		List<Map<String, Object>> marks = new ArrayList<>();
		for (String mediaId : rejectedMediaIds) {
			marks.add(Map.of("mediaId", mediaId, "reason", "Blurred"));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", review.version(checklistId));
		body.put("outcome", "NEEDS_CHANGES");
		body.put("needsChangesKind", "DOCUMENTS_ONLY");
		body.put("reason", "Bring a sharper kitchen photo");
		body.put("rejectedEvidence", marks);
		SurveyApiTestSupport.ok(checklists.api().post(leader, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId
				+ "/decision", body));
	}

	private JsonNode read(TestUser caller, String checklistId) throws Exception {
		return checklists.readOk(caller, checklistId).body();
	}

	private List<String> evidenceRows(String checklistId) {
		return jdbc.queryForList("select media_id from survey_checklist_evidence where checklist_id = ? order by media_id",
				String.class, checklistId);
	}

	/** The evidence items of the readiness as {@code placement/category/problem}. */
	private static List<String> missingEvidence(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		for (JsonNode item : checklist.path("readiness").path("missing")) {
			if ("EVIDENCE".equals(item.path("group").asString())) {
				items.add((item.path("spaceId").isNull() ? "asset" : item.path("spaceId").asString()) + "/"
						+ item.path("field").asString() + "/" + item.path("problem").asString());
			}
		}
		return items;
	}

	/** The frozen evidence entry of the photo in the revision's read; fails when the revision does not hold it. */
	private JsonNode evidenceEntry(JsonNode checklist, String mediaId) {
		for (JsonNode entry : checklist.path("submission").path("evidence")) {
			if (mediaId.equals(entry.path("mediaId").asString())) {
				return entry;
			}
		}
		throw new AssertionError("no evidence entry for " + mediaId + ": " + checklist.path("submission"));
	}

	private List<String> listedIds(String checklistId) throws Exception {
		Result list = media.list(sale, checklistId, null);
		assertEquals(200, list.status(), list.rawBody());
		List<String> ids = new ArrayList<>();
		list.body().path("items").forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	/** survey-checklist SV-211 RC-1, RC-2: the refused photo stays in revision #1, the others are carried. */
	@Test
	void revision_rejectedPhoto_staysBehind() throws Exception {
		Ready ready = review.ready(leader, sale);
		String blurred = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), blurred);

		String next = revised(sale, ready.checklistId(), null);

		assertEquals(ready.photos().stream().sorted().toList(), selected(next), "the refused photo is not carried");
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_checklist_media_selections where media_id = ?",
				Integer.class, blurred));
		assertEquals(ready.checklistId(), jdbc.queryForObject("select distinct carried_from_checklist_id from"
				+ " survey_checklist_media_selections where checklist_id = ?", String.class, next));
		JsonNode refused = evidenceEntry(read(sale, ready.checklistId()), blurred);
		assertTrue(refused.path("accepted").isBoolean() && !refused.path("accepted").asBoolean(), refused.toString());
		assertEquals("Blurred", refused.path("rejectionReason").asString());
		assertTrue(listedIds(ready.checklistId()).contains(blurred), "revision #1 still lists the refused photo");
		Map<String, Object> row = media.row(blurred);
		assertNull(row.get("removed_at"));
		assertNull(row.get("content_delete_requested_at"));
	}

	/** survey-checklist SV-211 RC-3: a pair whose only photo was refused is missing until a new photo is uploaded on it. */
	@Test
	void revision_onlyKitchenRejected_missingUntilReplaced() throws Exception {
		Ready ready = review.ready(leader, sale);
		String kitchen = ready.photos().get(2);
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), kitchen);
		String next = revised(sale, ready.checklistId(), null);

		JsonNode draft = read(sale, next);

		assertTrue(missingEvidence(draft).contains(ready.spaceId() + "/KITCHEN/EVIDENCE_MISSING"),
				missingEvidence(draft).toString());
		assertEquals("MISSING_ITEMS", draft.path("readiness").path("submissionUnavailableReason").asString());
		Result refused = review.submit(sale, next);
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("CHECKLIST_NOT_READY", refused.body().path("code").asString(), refused.rawBody());
		assertEquals("readiness.missing", refused.body().path("errors").path(0).path("field").asString(),
				refused.rawBody());

		String replacement = review.photo(sale, next, ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, next);

		List<String> frozen = new ArrayList<>(List.of(ready.photos().get(0), ready.photos().get(1),
				ready.photos().get(3), replacement));
		frozen.sort(null);
		assertEquals(frozen, evidenceRows(next), "the refused photo is not frozen again");
	}

	/**
	 * survey-checklist SV-211 RC-4: a revision started before the fix that still selects the refused photo does not
	 * pass it on, because the carry reads the marks of every decision of the visit.
	 */
	@Test
	void revision_rejectedOnEarlierRevision_neverCarriedAgain() throws Exception {
		Ready ready = review.ready(leader, sale);
		String blurred = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), blurred);
		String second = revised(sale, ready.checklistId(), null);
		jdbc.update("insert into survey_checklist_media_selections (checklist_id, media_id, carried_from_checklist_id,"
				+ " selected_at) values (?, ?, ?, ?) on conflict do nothing", second, blurred, ready.checklistId(),
				Timestamp.from(Instant.now()));
		review.submitted(sale, second);
		review.needsChanges(leader, second, "DOCUMENTS_ONLY");

		String third = revised(sale, second, null);

		assertEquals(ready.photos().stream().sorted().toList(), selected(third), "the refused photo is carried again");
	}

	/** survey-checklist SV-211 RC-5, SV211-D3: the same bytes upload again into the next revision as a new photo. */
	@Test
	void revision_rejectedPhotoBytes_uploadAgainAsNewPhoto() throws Exception {
		Ready ready = review.ready(leader, sale);
		String kitchen = ready.photos().get(2);
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), kitchen);
		String next = revised(sale, ready.checklistId(), null);
		Object sha = media.row(kitchen).get("source_sha256");
		assertNotNull(sha);

		Result created = media.create(sale, next, MediaApiTestSupport.exterior(sha.toString()));

		assertEquals(201, created.status(), created.rawBody());
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media where checklist_id = ? and"
				+ " source_sha256 = ?", Integer.class, next, sha.toString()));
	}
}
