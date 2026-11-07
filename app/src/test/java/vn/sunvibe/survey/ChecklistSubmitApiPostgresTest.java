package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.shared.checklist.SubmissionCanonicalForm;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionHash;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSubmissionDomainRepository;

/**
 * POST /survey-checklists/{id}/submission (survey-checklist submission spec 4.3, 4.4, 5.5, S-1..S-4, S-17): what a submit
 * freezes, each gate, the replay of A-4 and what can no longer change. Needs Docker.
 */
class ChecklistSubmitApiPostgresTest extends MediaApiTestBase {

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

	@Autowired
	private SubmissionCanonicalForm canonicalForm;

	@Autowired
	private SurveyChecklistGroupDomainRepository groups;

	@Autowired
	private SurveyChecklistSubmissionDomainRepository submissions;

	@Autowired
	private SurveyChecklistEvidenceDomainRepository evidence;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private ChecklistReviewApiSupport review;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0968");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Submit Sale", "SALE");
		otherSale = survey.user("Submit Other Sale", "SALE");
		leader = survey.user("Submit Leader", "SALE_LEADER");
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

	private static List<String> fields(Result result) {
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		return fields;
	}

	private void assertRefused(Result refused, int status, String code, String... fields) {
		assertEquals(status, refused.status(), refused.rawBody());
		assertEquals(code, refused.body().path("code").asString(), refused.rawBody());
		if (fields.length > 0) {
			assertEquals(List.of(fields), fields(refused), refused.rawBody());
		}
	}

	// ==================== what a submit freezes (S-1) ====================

	@Test
	void submit_freezesEvidenceAndHash() throws Exception {
		Ready ready = review.ready(leader, sale);
		long leadVersion = survey.version(ready.leadId());

		JsonNode answer = review.submitted(sale, ready.checklistId());

		assertEquals("SUBMITTED", answer.path("checklist").path("status").asString());
		assertEquals("CHECKLIST_SUBMITTED", answer.path("historyEntry").path("type").asString());
		SurveyChecklistId id = SurveyChecklistId.of(ready.checklistId());
		ChecklistSubmission stored = submissions.findByChecklistId(id).orElseThrow();
		assertEquals(sale.userId(), stored.submittedByUserId());
		assertEquals(ChecklistSubmission.MEDIA_V1_NO_PERMISSION, stored.mediaPolicy());
		assertEquals(ready.visit().appointmentId(), stored.visitAppointmentId().getValue());
		assertEquals(sale.userId(), stored.visitPerformedBy());
		assertEquals("WHOLE", stored.rentalScope().name());
		assertEquals(1, stored.spaces().size());
		assertEquals("A1", stored.spaces().get(0).code());
		assertEquals(7, stored.groupVersions().size());

		List<EvidenceEntry> frozen = evidence.findByChecklistId(id);
		assertEquals(new TreeSet<>(ready.photos()), new TreeSet<>(frozen.stream().map(EvidenceEntry::mediaId).toList()));
		for (EvidenceEntry entry : frozen) {
			assertEquals(jdbc.queryForObject("select sha256 from survey_media_variants where media_id = ?"
					+ " and variant = 'FULL'", String.class, entry.mediaId()), entry.fullSha256());
		}

		Map<ChecklistGroup, GroupContent> contents = new EnumMap<>(ChecklistGroup.class);
		groups.findAll(id).forEach((group, state) -> contents.put(group, state.document().content()));
		assertEquals(SubmissionHash.of(canonicalForm.of(contents, frozen)), stored.contentSha256(),
				"the groups and evidence read back hash to what was stored");

		assertEquals(leadVersion, survey.version(ready.leadId()), "a submit never writes the lead row");
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(ready.leadId()));
		assertEquals(1, jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?"
				+ " and type = 'CHECKLIST_SUBMITTED'", Integer.class, ready.leadId()));
	}

	@Test
	void submit_leadVersionUnchanged() throws Exception {
		Ready ready = review.ready(leader, sale);
		long version = survey.version(ready.leadId());

		review.submitted(sale, ready.checklistId());

		assertEquals(version, survey.version(ready.leadId()));
	}

	// ==================== the replay (S-3, A-4) ====================

	@Test
	void submit_sameCallerTwice_storesOnceAndReplays() throws Exception {
		Ready ready = review.ready(leader, sale);
		long version = review.version(ready.checklistId());
		review.submitted(sale, ready.checklistId());

		Result again = review.submit(sale, ready.checklistId(), version);

		assertEquals(200, again.status(), again.rawBody());
		assertTrue(again.body().path("historyEntry").isNull());
		assertEquals("SUBMITTED", again.body().path("checklist").path("status").asString());
		assertEquals(1, jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?"
				+ " and type = 'CHECKLIST_SUBMITTED'", Integer.class, ready.leadId()));
	}

	@Test
	void submit_decidedRevisionRepeated_isReadOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		jdbc.update("update survey_checklists set status = 'FAILED', decided_at = now() where id = ?",
				ready.checklistId());

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_READ_ONLY");
	}

	// ==================== who and when (spec 5.5) ====================

	@Test
	void submit_noVersion_400() throws Exception {
		Ready ready = review.ready(leader, sale);

		assertRefused(review.submit(sale, ready.checklistId(), null), 400, "VALIDATION_FAILED", "expectedVersion");
	}

	@Test
	void submit_staleVersion_409() throws Exception {
		Ready ready = review.ready(leader, sale);

		assertRefused(review.submit(sale, ready.checklistId(), review.version(ready.checklistId()) + 1), 409,
				"CONCURRENT_MODIFICATION", "expectedVersion");
	}

	@Test
	void submit_hiddenLead_404WithEvent() throws Exception {
		Ready ready = review.ready(leader, sale);

		assertEquals(404, review.submit(otherSale, ready.checklistId()).status());

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ?"
				+ " and action = 'SUBMIT' and outcome = 'NOT_VISIBLE'", Integer.class, ready.leadId()));
	}

	@Test
	void submit_notAssignee_403WithEvent() throws Exception {
		Ready ready = review.ready(leader, sale);

		assertEquals(403, review.submit(leader, ready.checklistId()).status());

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_security_events where lead_id = ?"
				+ " and action = 'SUBMIT' and outcome = 'NOT_ASSIGNEE'", Integer.class, ready.leadId()));
	}

	@Test
	void submit_withoutToken_401() throws Exception {
		Ready ready = review.ready(leader, sale);

		assertEquals(401, checklists.api().post(null, ChecklistReviewApiSupport.CHECKLISTS + "/" + ready.checklistId()
				+ "/submission", Map.of("expectedVersion", 0)).status());
	}

	// ==================== the gates (S-2, S-17) ====================

	@Test
	void submit_submissionOff_409() throws Exception {
		Ready ready = review.ready(leader, sale);
		switches.submission(false);

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_SUBMISSION_NOT_AVAILABLE", "submission");
	}

	@Test
	void submit_uploadsOff_409() throws Exception {
		Ready ready = review.ready(leader, sale);
		switches.uploads(false);

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_SUBMISSION_NOT_AVAILABLE", "submission");
	}

	@Test
	void submit_missingItem_refused() throws Exception {
		Ready ready = review.ready(leader, sale);
		checklists.saved(sale, ready.checklistId(), "parts", c -> ChecklistApiTestSupport.set(c, "accessRoute",
				checklists.unknown(false)));

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_NOT_READY", "readiness.missing");
	}

	@Test
	void submit_pendingPhoto_refused() throws Exception {
		Ready ready = review.ready(leader, sale);
		media.insertPhoto(ready.checklistId(), sale.userId(), Row.PROCESSING, Instant.now(),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_NOT_READY", "media.pending");
	}

	@Test
	void submit_heldPhoto_refused() throws Exception {
		Ready ready = review.ready(leader, sale);
		String extra = review.photo(sale, ready.checklistId(), null, null, "DETAIL");
		media.held(extra, leader.userId(), "SECURITY_INCIDENT");

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_NOT_READY", "media.unavailable");
	}

	@Test
	void submit_scheduledAppointment_refused() throws Exception {
		Ready ready = review.ready(leader, sale);
		Map<String, Object> resurvey = new java.util.LinkedHashMap<>(survey.booking(ready.leadId(), Instant.now()
				.plus(java.time.Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)));
		resurvey.put("resurveyReason", "The owner wants the kitchen seen again");
		assertEquals(201, survey.book(sale, ready.leadId(), resurvey).status());

		assertRefused(review.submit(sale, ready.checklistId()), 409, "CHECKLIST_NOT_READY", "appointment");
	}

	@Test
	void submit_warningsAndUnconfirmedTerms_doNotBlock() throws Exception {
		Ready ready = review.ready(leader, sale);
		JsonNode read = checklists.readOk(sale, ready.checklistId()).body();
		assertFalse(read.path("readiness").path("unconfirmedTerms").isEmpty(), "this record has unconfirmed terms");

		assertEquals("SUBMITTED", review.submitted(sale, ready.checklistId()).path("checklist").path("status")
				.asString());
	}

	@Test
	void read_allGatesPass_submissionAvailable() throws Exception {
		Ready ready = review.ready(leader, sale);

		JsonNode readiness = checklists.readOk(sale, ready.checklistId()).body().path("readiness");

		assertTrue(readiness.path("submissionAvailable").asBoolean(), readiness.toString());
		assertTrue(readiness.path("submissionUnavailableReason").isNull());
	}

	// ==================== after the freeze (S-4) ====================

	@Test
	void save_afterSubmit_409ReadOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());

		assertRefused(checklists.save(sale, ready.checklistId(), "parts", c -> ChecklistApiTestSupport.set(c,
				"accessRoute", checklists.value("Changed after the freeze"))), 409, "CHECKLIST_READ_ONLY");
		assertEquals("SUBMITTED", checklists.readOk(sale, ready.checklistId()).body().path("readOnlyReason").asString());
	}

	@Test
	void upload_afterSubmit_409ReadOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());

		assertRefused(media.create(sale, ready.checklistId(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				MediaApiTestSupport.newKey()))), 409, "CHECKLIST_READ_ONLY");
	}

	@Test
	void remove_afterSubmit_keepsThePhoto() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());

		assertRefused(media.remove(sale, ready.photos().get(0), null), 409, "CHECKLIST_READ_ONLY");

		assertNotNull(media.row(ready.photos().get(0)));
		assertEquals(null, media.row(ready.photos().get(0)).get("removed_at"));
	}

	@Test
	void takeover_afterSubmit_409ReadOnly() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + ready.leadId()
				+ "/assign", Map.of("assigneeUserId", otherSale.userId(), "expectedVersion",
				survey.version(ready.leadId()))));

		assertRefused(checklists.takeOver(otherSale, ready.checklistId(), ready.visit()), 409, "CHECKLIST_READ_ONLY");
	}
}
