package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.command.common.BasisInput;
import vn.sunvibe.survey.core.application.command.common.CorrectionInput;
import vn.sunvibe.survey.core.application.command.media.DeselectMediaCommand;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.service.media.MediaRestoreReconciliation;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;

/**
 * Photos of a later revision of one visit (survey-checklist submission spec 4.6, 5.4, S-13, A-12): a carried photo keeps
 * its rows and its origin, leaves a revision by deselection and never by removal, counts for the caps and the duplicate
 * check, and any change of it shows in every revision that selects it. Needs Docker.
 */
class MediaCarriedPostgresTest extends MediaApiTestBase {

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
	private SurveyMediaCommandUseCase commands;

	@Autowired
	private MediaContentDeletionJob deletion;

	@Autowired
	private MediaRestoreReconciliation reconciliation;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private ChecklistRevisionRows revisions;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0969");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		revisions = new ChecklistRevisionRows(jdbc, ulidGenerator::generateId);
		sale = survey.user("Carried Sale", "SALE");
		otherSale = survey.user("Carried Other Sale", "SALE");
		leader = survey.user("Carried Leader", "SALE_LEADER");
		admin = survey.user("Carried Admin", "ADMIN");
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

	private SurveyViewer viewer(TestUser user) {
		return new SurveyViewer(user.userId(), ViewerAccess.OWN);
	}

	/** A first revision with one READY photo of the uploader, returned for documents, and revision #2 carrying it. */
	private record Carried(Visit visit, String first, String second, String photo) {
	}

	private Carried carried(TestUser uploader, int seed) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String first = checklists.started(sale, visit.appointmentId());
		String photo = media.readyWithVariants(store, first, uploader.userId(), MediaApiTestSupport.bytes(3000, seed),
				MediaApiTestSupport.bytes(2000, seed + 1), MediaApiTestSupport.bytes(1000, seed + 2));
		String second = revisions.documentsOnlyReturn(first, leader.userId(), List.of(photo));
		return new Carried(visit, first, second, photo);
	}

	private static JsonNode ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	private List<String> selectedIds(String checklistId) throws Exception {
		List<String> ids = new ArrayList<>();
		ok(media.list(sale, checklistId, null)).path("items").forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	private CorrectionInput correction() {
		return new CorrectionInput("Ảnh sai chỗ", new BasisInput(LocalDate.now(), "Chủ nhà báo"));
	}

	// ==================== the list ====================

	@Test
	void list_carriedPhoto_showsItsRevision() throws Exception {
		Carried carried = carried(sale, 11);

		JsonNode item = ok(media.list(sale, carried.second(), null)).path("items").path(0);

		assertEquals(carried.photo(), item.path("id").asString());
		assertEquals(1, item.path("carriedFromChecklistNo").asInt());
		assertTrue(ok(media.list(sale, carried.first(), null)).path("items").path(0)
				.path("carriedFromChecklistNo").isNull(), "in its own revision the photo is no carried one");
	}

	// ==================== deselection ====================

	/** Review focus 4: #1 still lists and serves the photo after the deletion job ran. */
	@Test
	void deselect_carriedPhoto_keepsContentAndEarlierRevision() throws Exception {
		Carried carried = carried(sale, 21);
		long before = media.setVersion(carried.second());

		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), null));

		assertEquals(List.of(), selectedIds(carried.second()));
		assertEquals(List.of(carried.photo()), selectedIds(carried.first()));
		assertEquals(before + 1, media.setVersion(carried.second()));
		Map<String, Object> row = media.row(carried.photo());
		assertNull(row.get("removed_at"), "a deselection is never a removal");
		assertNull(row.get("content_delete_requested_at"), "and asks for no deletion");

		deletion.drain();

		assertEquals(200, media.read(sale, carried.photo(), "preview").status());
		assertNull(media.row(carried.photo()).get("content_deleted_at"));
		assertEquals(sale.userId(), jdbc.queryForObject("select deselected_by_user_id from"
				+ " survey_checklist_media_selections where checklist_id = ? and media_id = ?", String.class,
				carried.second(), carried.photo()));
	}

	@Test
	void deselect_writesTheHistoryOfARemoval() throws Exception {
		Carried carried = carried(sale, 25);

		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), null));

		assertEquals("DETAIL", jdbc.queryForObject("select reference_detail from owner_lead_activities"
				+ " where lead_id = ? and type = 'CHECKLIST_MEDIA_REMOVED'", String.class, carried.visit().leadId()));
	}

	@Test
	void deselect_twice_changesNothingMore() throws Exception {
		Carried carried = carried(sale, 28);
		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), null));
		long version = media.setVersion(carried.second());

		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), null));

		assertEquals(version, media.setVersion(carried.second()));
	}

	/** media BR-12: a committed photo of another uploader leaves only with a correction. */
	@Test
	void deselect_otherUploader_needsCorrection() throws Exception {
		Carried carried = carried(otherSale, 31);

		ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
				() -> commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(),
						null)));

		assertEquals("correction", refused.violations().get(0).path());
		assertEquals(List.of(carried.photo()), selectedIds(carried.second()));

		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), correction()));

		assertEquals("Ảnh sai chỗ", jdbc.queryForObject("select deselection_correction_reason from"
				+ " survey_checklist_media_selections where checklist_id = ?", String.class, carried.second()));
	}

	/** spec 5.5: a photo the revision uploaded itself leaves by removal, so here it is not found. */
	@Test
	void deselect_ownPhoto_404() throws Exception {
		Carried carried = carried(sale, 41);
		String own = media.insertPhoto(carried.second(), sale.userId(), Row.READY, Instant.now(),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));

		assertThrows(MediaNotFoundException.class,
				() -> commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), own, null)));
	}

	/** media spec 5.4: someone who sees the lead without being its assignee is refused, with a throttled event. */
	@Test
	void deselect_refused_writesSecurityEvent() throws Exception {
		Carried carried = carried(sale, 51);
		SurveyViewer notAssignee = new SurveyViewer(leader.userId(), ViewerAccess.ALL);

		assertThrows(AccessDeniedException.class, () -> commands.deselect(notAssignee,
				new DeselectMediaCommand(carried.second(), carried.photo(), null)));

		assertEquals(0, jdbc.queryForObject("select count(*) from survey_checklist_media_selections"
				+ " where checklist_id = ? and deselected_at is not null", Integer.class, carried.second()));
		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ?"
				+ " and action = 'DESELECT' and outcome = 'NOT_ASSIGNEE'", Integer.class, carried.visit().leadId()));
	}

	/** Someone who cannot see the lead gets the photo's 404, with the same kind of event. */
	@Test
	void deselect_hiddenLead_404AndEvent() throws Exception {
		Carried carried = carried(sale, 55);

		assertThrows(MediaNotFoundException.class, () -> commands.deselect(viewer(otherSale),
				new DeselectMediaCommand(carried.second(), carried.photo(), null)));

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ?"
				+ " and action = 'DESELECT' and outcome = 'NOT_VISIBLE'", Integer.class, carried.visit().leadId()));
	}

	// ==================== the G1 cascade ====================

	@Test
	void cascade_removedSpace_deselectsCarried() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String first = checklists.started(sale, visit.appointmentId());
		String space1 = checklists.newId();
		String space2 = checklists.newId();
		checklists.saved(sale, first, "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(space1, "P101"), checklists.space(space2, "P102"))));
		String onSpace2 = media.insertPhoto(first, sale.userId(), Row.READY, Instant.now(),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()), space2, "P102", "KITCHEN");
		String onSpace1 = media.insertPhoto(first, sale.userId(), Row.READY, Instant.now(),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()), space1, "P101", "KITCHEN");
		String second = revisions.documentsOnlyReturn(first, leader.userId(), List.of(onSpace2, onSpace1));

		checklists.saved(sale, second, "identification", g1 -> {
			ArrayNode spaces = (ArrayNode) g1.path("spaces");
			for (int i = spaces.size() - 1; i >= 0; i--) {
				if (space2.equals(spaces.path(i).path("spaceId").asString())) {
					spaces.remove(i);
				}
			}
		});

		assertEquals(List.of(onSpace1), selectedIds(second));
		assertEquals(2, selectedIds(first).size(), "the earlier revision keeps both");
		assertNull(media.row(onSpace2).get("removed_at"));
		Map<String, Object> selection = jdbc.queryForMap("select deselection_kind, deselected_by_save_id from"
				+ " survey_checklist_media_selections where checklist_id = ? and media_id = ?", second, onSpace2);
		assertEquals("CASCADE", selection.get("deselection_kind"));
		assertNotNull(selection.get("deselected_by_save_id"));
	}

	// ==================== caps and identity ====================

	/** spec 4.6: the cap of 100 counts own plus carried. */
	@Test
	void caps_countOwnPlusCarried() throws Exception {
		Carried carried = carried(sale, 61);
		media.insertPhotos(carried.second(), sale.userId(), Row.READY, 99, Instant.now(), 1000L, "caps");

		Result refused = media.create(sale, carried.second(), MediaApiTestSupport.exterior(
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey())));

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("MEDIA_LIMIT_REACHED", refused.body().path("code").asString());
	}

	@Test
	void duplicateContent_checksCarried() throws Exception {
		Carried carried = carried(sale, 71);
		String sha = media.row(carried.photo()).get("source_sha256").toString();

		Result refused = media.create(sale, carried.second(), MediaApiTestSupport.exterior(sha));

		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("MEDIA_DUPLICATE_CONTENT", refused.body().path("code").asString());
	}

	/** Once the revision let go of a carried photo its content may come back as an own photo. */
	@Test
	void duplicateContent_afterDeselection_allowed() throws Exception {
		Carried carried = carried(sale, 75);
		String sha = media.row(carried.photo()).get("source_sha256").toString();
		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), null));

		assertEquals(201, media.create(sale, carried.second(), MediaApiTestSupport.exterior(sha)).status());
	}

	// ==================== changes of a carried photo ====================

	@Test
	void hold_carriedPhoto_bumpsEverySelectingSet() throws Exception {
		Carried carried = carried(sale, 81);
		long first = media.setVersion(carried.first());
		long second = media.setVersion(carried.second());

		ok(checklists.api().post(admin, MediaApiTestSupport.MEDIA + "/" + carried.photo() + "/hold",
				Map.of("reason", "SECURITY_INCIDENT")));

		assertEquals(first + 1, media.setVersion(carried.first()));
		assertEquals(second + 1, media.setVersion(carried.second()));

		ok(checklists.api().post(admin, MediaApiTestSupport.MEDIA + "/" + carried.photo() + "/hold-release", Map.of()));

		assertEquals(first + 2, media.setVersion(carried.first()));
		assertEquals(second + 2, media.setVersion(carried.second()));
	}

	/** Deselected, the revision no longer shows the photo, so a hold says nothing to it. */
	@Test
	void hold_afterDeselection_leavesThatSetAlone() throws Exception {
		Carried carried = carried(sale, 85);
		commands.deselect(viewer(sale), new DeselectMediaCommand(carried.second(), carried.photo(), null));
		long second = media.setVersion(carried.second());

		ok(checklists.api().post(admin, MediaApiTestSupport.MEDIA + "/" + carried.photo() + "/hold",
				Map.of("reason", "SECURITY_INCIDENT")));

		assertEquals(second, media.setVersion(carried.second()));
	}

	@Test
	void reconciliation_missingContent_bumpsEverySelectingSet() throws Exception {
		Carried carried = carried(sale, 91);
		long first = media.setVersion(carried.first());
		long second = media.setVersion(carried.second());
		store.delete(vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket.MEDIA,
				"media/" + carried.photo() + "/1/PREVIEW");

		reconciliation.run();

		assertNotNull(media.row(carried.photo()).get("content_missing_at"));
		assertEquals(first + 1, media.setVersion(carried.first()));
		assertEquals(second + 1, media.setVersion(carried.second()));
		assertFalse(ok(media.list(sale, carried.second(), null)).path("items").path(0).path("contentAvailable")
				.asBoolean());
	}
}
