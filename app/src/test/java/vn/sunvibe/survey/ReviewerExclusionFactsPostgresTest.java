package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.core.application.command.common.BasisInput;
import vn.sunvibe.survey.core.application.command.common.CorrectionInput;
import vn.sunvibe.survey.core.application.command.media.DeselectMediaCommand;
import vn.sunvibe.survey.core.application.shared.checklist.ReviewerExclusionLoader;
import vn.sunvibe.survey.core.application.shared.checklist.ReviewerExclusionLoader.Target;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaCommandUseCase;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusion;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusionFacts;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;

/**
 * Who cannot review a revision, each source made through the real commands (survey-checklist submission spec 4.2, plan
 * PD-2): {@code suspect} touches the record one way and hands the lead back. Every case also proves the batched read
 * gives the same facts as the single one. Needs Docker.
 */
class ReviewerExclusionFactsPostgresTest extends MediaApiTestBase {

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
	private ReviewerExclusionLoader loader;

	@Autowired
	private SurveyChecklistDomainRepository revisions;

	@Autowired
	private SurveyAppointmentDomainRepository appointments;

	@Autowired
	private SurveyMediaCommandUseCase mediaCommands;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private ChecklistReviewApiSupport review;
	private TestUser sale;
	private TestUser suspect;
	private TestUser leader;
	private TestUser manager;
	private Target other;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0963");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Exclusion Sale", "SALE");
		suspect = survey.user("Exclusion Suspect", "SALE_LEADER");
		leader = survey.user("Exclusion Leader", "SALE_LEADER");
		manager = survey.user("Exclusion Manager", "SALES_MANAGER");
	}

	@BeforeEach
	void open() throws Exception {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
		Visit visit = checklists.visited(leader, sale);
		other = target(checklists.started(sale, visit.appointmentId()));
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

	private Target target(String checklistId) {
		SurveyChecklist revision = revisions.findById(SurveyChecklistId.of(checklistId)).orElseThrow();
		SurveyAppointment visit = appointments.findById(revision.getVisitAppointmentId()).orElseThrow();
		return new Target(revision, visit);
	}

	/** The facts of the revision, the same read alone and in a batch with a revision of another lead. */
	private ReviewerExclusionFacts facts(String checklistId) {
		Target target = target(checklistId);
		ReviewerExclusionFacts alone = loader.load(target.revision(), target.visit());
		assertEquals(alone, loader.loadAll(List.of(other, target)).get(target.revision().getId()),
				"the batched read answers as the single one");
		return alone;
	}

	private void assertExcluded(ReviewerExclusionFacts facts) {
		assertTrue(ReviewerExclusion.excluded(facts, sale.userId()).contains(suspect.userId()));
		assertFalse(ReviewerExclusion.excluded(facts, sale.userId()).contains(manager.userId()));
	}

	private static Map<String, Object> intake() {
		return ChecklistApiTestSupport.intake("Took the lead over", LocalDate.now(SurveyTimeRules.ZONE), "Leader note");
	}

	private String startedWithIntake(TestUser caller, String appointmentId) throws Exception {
		Result started = checklists.start(caller, appointmentId, intake());
		assertEquals(201, started.status(), started.rawBody());
		return started.body().path("checklist").path("id").asString();
	}

	/** Sale's draft taken over by the suspect, who holds it now. */
	private Draft heldBySuspect() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		return handedToSuspect(new Draft(visit, id));
	}

	private Draft handedToSuspect(Draft draft) throws Exception {
		checklists.reassign(leader, draft.visit().leadId(), suspect);
		assertEquals(201, checklists.takeOver(suspect, draft.id(), draft.visit()).status());
		return draft;
	}

	private void handedBack(Draft draft) throws Exception {
		checklists.reassign(leader, draft.visit().leadId(), sale);
		assertEquals(201, checklists.takeOver(sale, draft.id(), draft.visit()).status());
	}

	private record Draft(Visit visit, String id) {
	}

	private JsonNode reviewOf(TestUser viewer, String checklistId) throws Exception {
		return checklists.readOk(viewer, checklistId).body().path("review");
	}

	private String returnedForDocuments(Ready ready) throws Exception {
		review.needsChanges(manager, ready.checklistId(), "DOCUMENTS_ONLY");
		return ready.checklistId();
	}

	private String revised(TestUser caller, String checklistId, Map<String, Object> intakeOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", intakeOrNull);
		Result answer = checklists.api().post(caller, ChecklistReviewApiSupport.CHECKLISTS + "/" + checklistId
				+ "/revisions", body);
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("checklist").path("id").asString();
	}

	@Test
	void ownPerformer_excluded_andTheReadSaysSo() throws Exception {
		Visit visit = checklists.visited(leader, suspect);
		checklists.reassign(leader, visit.leadId(), sale);
		String id = startedWithIntake(sale, visit.appointmentId());
		String space = checklists.newId();
		review.fill(sale, id, space);
		review.photos(sale, id, space);
		review.submitted(sale, id);

		assertEquals(suspect.userId(), facts(id).performerUserId());
		assertExcluded(facts(id));
		assertEquals("NOT_INDEPENDENT", reviewOf(suspect, id).path("reason").asString());
		assertFalse(reviewOf(suspect, id).path("canReview").asBoolean());
		assertTrue(reviewOf(manager, id).path("canReview").asBoolean());
	}

	@Test
	void proxyRecorder_excluded() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		Instant start = SurveyApiTestSupport.startIn(24);
		String appointment = survey.booked(sale, leadId, start);
		Duration back = Duration.ofDays(2).plusHours(1);
		survey.shiftBack(appointment, back);
		Instant arrived = start.minus(back);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(leadId));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(appointment));
		body.put("arrivedAt", arrived.toString());
		body.put("note", "Met the owner");
		body.put("performedByUserId", sale.userId());
		body.put("proxy", Map.of("reason", "No signal", "basis", Map.of("confirmedOn",
				SurveyTimeRules.vietnamDate(arrived).toString(), "text", "Sale called me")));
		SurveyApiTestSupport.ok(checklists.api().post(suspect, SurveyApiTestSupport.APPOINTMENTS + "/" + appointment
				+ "/visit", body));
		String id = checklists.started(sale, appointment);

		assertEquals(suspect.userId(), facts(id).recorderUserId());
		assertEquals(sale.userId(), facts(id).performerUserId());
		assertExcluded(facts(id));
	}

	@Test
	void startByOther_excluded() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		checklists.reassign(leader, visit.leadId(), suspect);
		String id = startedWithIntake(suspect, visit.appointmentId());
		handedBack(new Draft(visit, id));

		assertTrue(facts(id).holders().contains(suspect.userId()));
		assertExcluded(facts(id));
	}

	@Test
	void takeoverTarget_excluded_afterHandingBack() throws Exception {
		Draft draft = heldBySuspect();
		handedBack(draft);

		assertTrue(facts(draft.id()).holders().contains(suspect.userId()));
		assertTrue(facts(draft.id()).holders().contains(sale.userId()), "the current holder too");
		assertExcluded(facts(draft.id()));
	}

	@Test
	void revisionByOther_excluded() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		String first = returnedForDocuments(ready);
		checklists.reassign(leader, ready.leadId(), suspect);
		String second = revised(suspect, first, intake());
		handedBack(new Draft(ready.visit(), second));

		assertTrue(facts(second).holders().contains(suspect.userId()));
		assertFalse(facts(first).holders().contains(suspect.userId()), "only revisions up to the one asked about");
		assertExcluded(facts(second));
	}

	@Test
	void groupSave_excluded() throws Exception {
		Draft draft = heldBySuspect();
		checklists.saved(suspect, draft.id(), "safety", c -> ChecklistApiTestSupport.set(c, "egress",
				checklists.value("Front door")));
		handedBack(draft);

		assertTrue(facts(draft.id()).savers().contains(suspect.userId()));
	}

	@Test
	void upload_excluded_alsoWhenRemovedLater() throws Exception {
		Draft draft = heldBySuspect();
		String photo = media.created(suspect, draft.id(), MediaApiTestSupport.bytes(1500, 301));
		SurveyApiTestSupport.ok(media.remove(suspect, photo, null));
		handedBack(draft);

		assertTrue(facts(draft.id()).uploaders().contains(suspect.userId()));
	}

	@Test
	void holderRemoval_excluded() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String photo = review.photo(sale, id, null, null, "EXTERIOR_ACCESS");
		Draft draft = handedToSuspect(new Draft(visit, id));
		SurveyApiTestSupport.ok(media.remove(suspect, photo, ChecklistApiTestSupport.correction("Wrong photo",
				visit.arrivalDate(), "Owner confirmed")));
		handedBack(draft);

		assertTrue(facts(id).removers().contains(suspect.userId()));
		assertFalse(facts(id).uploaders().contains(suspect.userId()));
	}

	@Test
	void cascadeRemoval_excluded() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String space = checklists.newId();
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(space, "A1"))));
		String photo = review.photo(sale, id, space, "A1", "KITCHEN");
		Draft draft = handedToSuspect(new Draft(visit, id));
		checklists.saved(suspect, id, "identification", ChecklistApiTestSupport.correction("Wrong space",
				visit.arrivalDate(), "Owner confirmed"), c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(checklists.newId(), "A2"))));
		assertEquals("CASCADE", media.row(photo).get("removal_kind"));
		handedBack(draft);

		assertTrue(facts(id).removers().contains(suspect.userId()));
	}

	/** A takeover ends open uploads without a remover: its target is excluded as a holder, whatever it ended. */
	@Test
	void takeoverEndingAnUpload_excludedAsHolder() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String open = media.created(sale, id, MediaApiTestSupport.bytes(1500, 311));
		Draft draft = handedToSuspect(new Draft(visit, id));
		assertEquals("UPLOAD_EXPIRED", media.row(open).get("status"));
		handedBack(draft);

		assertFalse(facts(id).removers().contains(suspect.userId()));
		assertTrue(facts(id).holders().contains(suspect.userId()));
		assertExcluded(facts(id));
	}

	@Test
	void deselect_excluded() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		String first = returnedForDocuments(ready);
		checklists.reassign(leader, ready.leadId(), suspect);
		String second = revised(suspect, first, intake());
		mediaCommands.deselect(new SurveyViewer(suspect.userId(), ViewerAccess.ALL), new DeselectMediaCommand(second,
				ready.photos().get(0), new CorrectionInput("Wrong photo", new BasisInput(LocalDate.now(SurveyTimeRules.ZONE),
				"Owner confirmed"))));

		assertTrue(facts(second).deselectors().contains(suspect.userId()));
		assertFalse(facts(first).deselectors().contains(suspect.userId()));
	}

	@Test
	void earlierSubmitter_excluded() throws Exception {
		Ready ready = review.ready(leader, suspect);
		review.submitted(suspect, ready.checklistId());
		String first = returnedForDocuments(ready);
		checklists.reassign(leader, ready.leadId(), sale);
		String second = revised(sale, first, intake());

		assertTrue(facts(second).submitters().contains(suspect.userId()));
		assertExcluded(facts(second));
	}

	/** D3-14: nothing but the assignment puts the leader out, and only while it lasts. */
	@Test
	void assigneeAfterReassignment_excluded() throws Exception {
		Ready ready = review.ready(leader, sale);
		review.submitted(sale, ready.checklistId());
		assertTrue(reviewOf(suspect, ready.checklistId()).path("canReview").asBoolean());

		checklists.reassign(leader, ready.leadId(), suspect);

		assertEquals("NOT_INDEPENDENT", reviewOf(suspect, ready.checklistId()).path("reason").asString());

		checklists.reassign(leader, ready.leadId(), sale);

		assertTrue(reviewOf(suspect, ready.checklistId()).path("canReview").asBoolean());
	}
}
