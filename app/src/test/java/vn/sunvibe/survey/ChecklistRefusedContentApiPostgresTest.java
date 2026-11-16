package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

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
 * survey-checklist SV-310: a frozen photo names the photos that a reviewer of the visit refused earlier with the same
 * content, and a revision names the refused photos of its previous revision that it does not hold (SV-310 spec RC-1 to
 * RC-6). The flag blocks nothing, a revision's own and later marks are not read, and the lists reach the readers of the
 * lead only. Needs Docker.
 */
class ChecklistRefusedContentApiPostgresTest extends MediaApiTestBase {

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
	private TestUser otherSale;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0941");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Refused Sale", "SALE");
		leader = survey.user("Refused Leader", "SALE_LEADER");
		otherSale = survey.user("Refused Other Sale", "SALE");
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

	/** The frozen evidence entry of the photo in the revision's read; fails when the revision does not hold it. */
	private JsonNode evidenceEntry(JsonNode checklist, String mediaId) {
		for (JsonNode entry : checklist.path("submission").path("evidence")) {
			if (mediaId.equals(entry.path("mediaId").asString())) {
				return entry;
			}
		}
		throw new AssertionError("no evidence entry for " + mediaId + ": " + checklist.path("submission"));
	}

	/** Gives the photo the source hash of another one, as the upload of the same bytes would (media A-16). */
	private void sameSource(String mediaId, String asMediaId) {
		assertEquals(1, jdbc.update("update survey_media set source_sha256 = (select source_sha256 from survey_media"
				+ " where id = ?) where id = ?", asMediaId, mediaId));
	}

	/** The items of a list of refused photos as {@code mediaId/checklistId/checklistNo/category/spaceId/spaceCode/reason}. */
	private static List<String> refused(JsonNode list) {
		assertTrue(list.isArray(), "not an array: " + list);
		List<String> items = new ArrayList<>();
		for (JsonNode item : list) {
			List<String> parts = new ArrayList<>();
			for (String field : List.of("mediaId", "checklistId", "checklistNo", "category", "spaceId", "spaceCode",
					"reason")) {
				JsonNode value = item.path(field);
				parts.add(value.isNull() ? "null" : value.asString());
			}
			items.add(String.join("/", parts));
		}
		return items;
	}

	/** The expected item of a refused kitchen photo of space code A1. */
	private static String item(String mediaId, String checklistId, int checklistNo, String spaceId, String reason) {
		return String.join("/", mediaId, checklistId, String.valueOf(checklistNo), "KITCHEN", spaceId, "A1", reason);
	}

	private int numberOf(String checklistId) throws Exception {
		return read(sale, checklistId).path("checklistNo").asInt();
	}

	/** Every frozen photo of the read has an empty {@code refusedSameContent}. */
	private static void noneFlagged(JsonNode checklist) {
		JsonNode evidence = checklist.path("submission").path("evidence");
		assertTrue(evidence.isArray() && !evidence.isEmpty(), "no frozen photos: " + checklist.path("submission"));
		for (JsonNode entry : evidence) {
			assertEquals(List.of(), refused(entry.path("refusedSameContent")), entry.toString());
		}
	}

	/** RC-1, RC-2, RC-6: the photo with the content of a refused one is flagged, the flag blocks nothing. */
	@Test
	void review_photoRepeatingRefusedContent_isFlagged() throws Exception {
		Ready ready = review.ready(leader, sale);
		String refusedPhoto = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), refusedPhoto);
		String next = revised(sale, ready.checklistId(), null);
		String again = review.photo(sale, next, ready.spaceId(), "A1", "KITCHEN");
		sameSource(again, refusedPhoto);
		review.submitted(sale, next);

		JsonNode read = read(leader, next);

		List<String> expected = List.of(item(refusedPhoto, ready.checklistId(), numberOf(ready.checklistId()),
				ready.spaceId(), "Blurred"));
		assertEquals(expected, refused(evidenceEntry(read, again).path("refusedSameContent")));
		for (JsonNode entry : read.path("submission").path("evidence")) {
			if (!again.equals(entry.path("mediaId").asString())) {
				assertEquals(List.of(), refused(entry.path("refusedSameContent")), entry.toString());
			}
		}
		assertEquals(expected, refused(evidenceEntry(read(sale, next), again).path("refusedSameContent")),
				"any reader of the lead gets the same list");
		JsonNode blockedBy = read.path("review").path("decisionBlockedBy");
		assertTrue(blockedBy.isArray() && blockedBy.isEmpty(), blockedBy.toString());
		review.passed(leader, next);
		assertError(checklists.read(otherSale, next), 404, "NOT_FOUND", null);
	}

	/** RC-3: a decision's own marks and a later decision's marks are not read. */
	@Test
	void review_ownAndLaterMarks_notFlagged() throws Exception {
		Ready ready = review.ready(leader, sale);
		String refusedPhoto = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), refusedPhoto);

		JsonNode first = read(sale, ready.checklistId());
		noneFlagged(first);
		JsonNode own = evidenceEntry(first, refusedPhoto);
		assertTrue(own.path("accepted").isBoolean() && !own.path("accepted").asBoolean(), own.toString());
		assertEquals("Blurred", own.path("rejectionReason").asString());

		String next = revised(sale, ready.checklistId(), null);
		String again = review.photo(sale, next, ready.spaceId(), "A1", "KITCHEN");
		sameSource(again, refusedPhoto);
		review.submitted(sale, next);
		returnedWithMarks(next, again);

		assertEquals(List.of(item(refusedPhoto, ready.checklistId(), numberOf(ready.checklistId()), ready.spaceId(),
				"Blurred")), refused(evidenceEntry(read(sale, next), again).path("refusedSameContent")));
		noneFlagged(read(sale, ready.checklistId()));
	}

	/** RC-1, RC-5: a second round lists both refusals newest first; the notice reads the previous revision only. */
	@Test
	void revision_secondRound_listsBothRefusalsNewestFirst() throws Exception {
		Ready ready = review.ready(leader, sale);
		String refusedPhoto = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), refusedPhoto);
		String next = revised(sale, ready.checklistId(), null);
		String again = review.photo(sale, next, ready.spaceId(), "A1", "KITCHEN");
		sameSource(again, refusedPhoto);
		review.submitted(sale, next);
		returnedWithMarks(next, again);

		String third = revised(sale, next, null);

		String refusedInNext = item(again, next, numberOf(next), ready.spaceId(), "Blurred");
		assertEquals(List.of(refusedInNext), refused(read(sale, third).path("media").path("refusedNotCarried")),
				"only the refused photos of the previous revision's frozen set");
		String last = review.photo(sale, third, ready.spaceId(), "A1", "KITCHEN");
		sameSource(last, refusedPhoto);
		review.submitted(sale, third);
		assertEquals(List.of(refusedInNext, item(refusedPhoto, ready.checklistId(), numberOf(ready.checklistId()),
				ready.spaceId(), "Blurred")), refused(evidenceEntry(read(leader, third), last)
				.path("refusedSameContent")));
	}

	/** RC-4: a revision started before SV-211 that holds the refused photo flags it; a second refusal is listed once. */
	@Test
	void review_carriedRefusedPhoto_flaggedWithItsOwnRefusal() throws Exception {
		Ready ready = review.ready(leader, sale);
		String refusedPhoto = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), refusedPhoto);
		String second = revised(sale, ready.checklistId(), null);
		jdbc.update("insert into survey_checklist_media_selections (checklist_id, media_id, carried_from_checklist_id,"
				+ " selected_at) values (?, ?, ?, ?) on conflict do nothing", second, refusedPhoto, ready.checklistId(),
				Timestamp.from(Instant.now()));
		assertTrue(selected(second).contains(refusedPhoto), "the revision started before SV-211 holds the photo");

		assertEquals(List.of(), refused(read(sale, second).path("media").path("refusedNotCarried")),
				"a photo the revision holds is not listed as not carried");
		review.submitted(sale, second);
		String refusedInFirst = item(refusedPhoto, ready.checklistId(), numberOf(ready.checklistId()), ready.spaceId(),
				"Blurred");
		assertEquals(List.of(refusedInFirst), refused(evidenceEntry(read(leader, second), refusedPhoto)
				.path("refusedSameContent")));
		returnedWithMarks(second, refusedPhoto);
		String third = revised(sale, second, null);

		String refusedInSecond = item(refusedPhoto, second, numberOf(second), ready.spaceId(), "Blurred");
		assertEquals(List.of(refusedInSecond), refused(read(sale, third).path("media").path("refusedNotCarried")),
				"once, with the newer refusal");
		String last = review.photo(sale, third, ready.spaceId(), "A1", "KITCHEN");
		sameSource(last, refusedPhoto);
		review.submitted(sale, third);
		assertEquals(List.of(refusedInSecond, refusedInFirst), refused(evidenceEntry(read(leader, third), last)
				.path("refusedSameContent")), "one item per refusing decision, newest first");
	}

	/** RC-5: the new revision names the refused photo it does not hold, from its first answer on. */
	@Test
	void revision_refusedPhoto_listedAsNotCarried() throws Exception {
		Ready ready = review.ready(leader, sale);
		String refusedPhoto = review.photo(sale, ready.checklistId(), ready.spaceId(), "A1", "KITCHEN");
		review.submitted(sale, ready.checklistId());
		returnedWithMarks(ready.checklistId(), refusedPhoto);

		Result started = revise(sale, ready.checklistId(), null);

		assertEquals(201, started.status(), started.rawBody());
		List<String> expected = List.of(item(refusedPhoto, ready.checklistId(), numberOf(ready.checklistId()),
				ready.spaceId(), "Blurred"));
		assertEquals(expected, refused(started.body().path("checklist").path("media").path("refusedNotCarried")));
		String next = started.body().path("checklist").path("id").asString();
		assertEquals(expected, refused(read(leader, next).path("media").path("refusedNotCarried")));
		assertEquals(List.of(), refused(read(sale, ready.checklistId()).path("media").path("refusedNotCarried")),
				"a first revision lists nothing");
		String again = review.photo(sale, next, ready.spaceId(), "A1", "KITCHEN");
		sameSource(again, refusedPhoto);
		assertEquals(expected, refused(read(sale, next).path("media").path("refusedNotCarried")),
				"still listed after the same bytes are uploaded again");
	}

	/** RC-1, RC-5: a return without marks lists nothing anywhere. */
	@Test
	void revision_returnWithoutMarks_listsNothing() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		review.needsChanges(leader, ready.checklistId(), "DOCUMENTS_ONLY");
		String next = revised(sale, ready.checklistId(), null);

		assertEquals(List.of(), refused(read(sale, next).path("media").path("refusedNotCarried")));
		review.submitted(sale, next);
		noneFlagged(read(sale, next));
		noneFlagged(read(sale, ready.checklistId()));
	}
}
