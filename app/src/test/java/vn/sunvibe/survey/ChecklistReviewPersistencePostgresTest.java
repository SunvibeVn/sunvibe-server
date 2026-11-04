package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.domain.exception.ChecklistSubmittedConflictException;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistKind;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.PropertyTypeCode;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SubmittedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSubmissionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSelectionDomainRepository;

/**
 * The rows of a submission and its review through the adapters (survey-checklist submission spec 5.3, plan PD-8):
 * every new row reads back as written, the checklist row keeps the new columns across an update, and the index of one
 * SUBMITTED revision per cycle answers its own exception. Needs Docker.
 */
class ChecklistReviewPersistencePostgresTest extends MediaApiTestBase {

	private static final Instant T0 = Instant.parse("2026-11-04T02:00:00Z");
	private static final String SHA = "b".repeat(64);

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
	private PlatformTransactionManager transactionManager;

	@Autowired
	private SurveyChecklistDomainRepository checklistRows;

	@Autowired
	private SurveyChecklistSubmissionDomainRepository submissions;

	@Autowired
	private SurveyChecklistEvidenceDomainRepository evidence;

	@Autowired
	private SurveyChecklistDecisionDomainRepository decisions;

	@Autowired
	private SurveyMediaSelectionDomainRepository selections;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0975");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Review Rows Sale", "SALE");
		leader = survey.user("Review Rows Leader", "SALE_LEADER");
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private String draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return checklists.started(sale, visit.appointmentId());
	}

	/** The checklist as SUBMITTED, with its submission row. */
	private SurveyChecklistId submitted(String checklistId) {
		SurveyChecklistId id = SurveyChecklistId.of(checklistId);
		transaction().executeWithoutResult(status -> {
			SurveyChecklist checklist = checklistRows.findByIdForUpdate(id).orElseThrow();
			checklist.submit(sale.userId(), T0);
			checklistRows.update(checklist);
			submissions.save(submission(checklist));
		});
		return id;
	}

	private ChecklistSubmission submission(SurveyChecklist checklist) {
		Map<ChecklistGroup, Long> versions = new EnumMap<>(ChecklistGroup.class);
		versions.put(ChecklistGroup.IDENTIFICATION, 3L);
		versions.put(ChecklistGroup.TERMS, 1L);
		return new ChecklistSubmission(checklist.getId(), sale.userId(), T0, checklist.getTemplateCode(),
				checklist.getTemplateVersion(), ChecklistKind.FULL_ASSESSMENT, "LONG_TERM_RENT", 1,
				checklist.getVisitAppointmentId(), sale.userId(), T0.minusSeconds(3600), leader.userId(),
				T0.minusSeconds(1800), RentalScope.PARTS, PropertyTypeCode.APARTMENT,
				List.of(new SubmittedSpace("01JSPACE000000000000000001", "P101")), versions,
				ChecklistSubmission.MEDIA_V1_NO_PERMISSION, SHA);
	}

	@Test
	void checklistRow_keepsTheNewColumnsAcrossAnUpdate() throws Exception {
		SurveyChecklistId id = submitted(draft());

		SurveyChecklist stored = checklistRows.findById(id).orElseThrow();

		assertEquals(ChecklistStatus.SUBMITTED, stored.getStatus());
		assertEquals(sale.userId(), stored.getSubmittedByUserId());
		assertEquals(T0, stored.getSubmittedAt());
		assertNull(stored.getDecidedAt());

		transaction().executeWithoutResult(status -> {
			SurveyChecklist checklist = checklistRows.findByIdForUpdate(id).orElseThrow();
			checklist.decide(DecisionOutcome.PASSED, T0.plusSeconds(60));
			checklistRows.update(checklist);
		});

		SurveyChecklist decided = checklistRows.findById(id).orElseThrow();
		assertEquals(ChecklistStatus.PASSED, decided.getStatus());
		assertEquals(T0, decided.getSubmittedAt(), "the submission survives the decision's update");
		assertEquals(T0.plusSeconds(60), decided.getDecidedAt());
	}

	@Test
	void submission_roundTrip() throws Exception {
		SurveyChecklistId id = submitted(draft());

		ChecklistSubmission stored = submissions.findByChecklistId(id).orElseThrow();

		assertEquals(sale.userId(), stored.submittedByUserId());
		assertEquals("LONG_TERM_RENT", stored.leadIntent());
		assertEquals(leader.userId(), stored.visitRecordedBy());
		assertEquals(RentalScope.PARTS, stored.rentalScope());
		assertEquals(PropertyTypeCode.APARTMENT, stored.propertyTypeOrNull());
		assertEquals(List.of(new SubmittedSpace("01JSPACE000000000000000001", "P101")), stored.spaces());
		assertEquals(Map.of(ChecklistGroup.IDENTIFICATION, 3L, ChecklistGroup.TERMS, 1L), stored.groupVersions());
		assertEquals(ChecklistSubmission.MEDIA_V1_NO_PERMISSION, stored.mediaPolicy());
		assertEquals(SHA, stored.contentSha256());
	}

	@Test
	void evidence_roundTrip_andReference() throws Exception {
		String checklistId = draft();
		String photo = media.insertPhoto(checklistId, sale.userId(), Row.READY, T0, MediaApiTestSupport.sha("rows-1"));
		String other = media.insertPhoto(checklistId, sale.userId(), Row.READY, T0, MediaApiTestSupport.sha("rows-2"));
		SurveyChecklistId id = submitted(checklistId);
		EvidenceEntry entry = new EvidenceEntry(photo, EvidenceCategory.MAIN_SPACE, "01JSPACE000000000000000001",
				"P101", SHA);

		transaction().executeWithoutResult(status -> evidence.saveAll(id, List.of(entry)));

		assertEquals(List.of(entry), evidence.findByChecklistId(id));
		assertEquals(Set.of(photo), evidence.findReferencedAmong(List.of(SurveyMediaId.of(photo),
				SurveyMediaId.of(other))));
		assertEquals(Set.of(), evidence.findReferencedAmong(List.of()));
	}

	@Test
	void decision_roundTrip_withItsMarks() throws Exception {
		String checklistId = draft();
		String photo = media.insertPhoto(checklistId, sale.userId(), Row.READY, T0, MediaApiTestSupport.sha("rows-3"));
		SurveyChecklistId id = submitted(checklistId);
		transaction().executeWithoutResult(status -> evidence.saveAll(id, List.of(new EvidenceEntry(photo,
				EvidenceCategory.EXTERIOR_ACCESS, null, null, SHA))));
		ChecklistDecision decision = new ChecklistDecision(id, DecisionOutcome.NEEDS_CHANGES,
				NeedsChangesKind.DOCUMENTS_ONLY, null, "Thiếu sổ đỏ", "Ghi chú", null, leader.userId(),
				T0.plusSeconds(120), "SURVEY_SCHEDULED", "SURVEY_SCHEDULED",
				List.of(new EvidenceRejection(photo, "Ảnh mờ")));

		transaction().executeWithoutResult(status -> decisions.save(decision));

		assertEquals(decision, decisions.findByChecklistId(id).orElseThrow());
		assertEquals(Map.of(id, decision), decisions.findByChecklistIds(List.of(id)));
	}

	@Test
	void decisions_undecidedRevision_isAbsent() throws Exception {
		SurveyChecklistId id = submitted(draft());

		assertTrue(decisions.findByChecklistId(id).isEmpty());
		assertEquals(Map.of(), decisions.findByChecklistIds(List.of()));
	}

	@Test
	void selection_roundTrip_deselectionAndSelectingRevisions() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String origin = checklists.started(sale, visit.appointmentId());
		String photo = media.insertPhoto(origin, sale.userId(), Row.READY, T0, MediaApiTestSupport.sha("rows-4"));
		submitted(origin);
		String next = secondRevision(origin);
		SurveyMediaId id = SurveyMediaId.of(photo);
		MediaSelection carried = MediaSelection.carried(next, id, origin, T0);

		transaction().executeWithoutResult(status -> selections.insertAll(List.of(carried)));

		assertEquals(List.of(carried), selections.findByChecklistId(next));
		assertEquals(List.of(next), selections.findSelectingChecklistIds(id));

		MediaRemovalCorrection correction = new MediaRemovalCorrection("Ảnh sai chỗ",
				new Basis(LocalDate.parse("2026-11-03"), "Chủ nhà báo"));
		MediaSelection deselected = carried.deselected(sale.userId(), RemovalKind.HOLDER, correction, null,
				T0.plusSeconds(60));
		transaction().executeWithoutResult(status -> selections.update(deselected));

		assertEquals(deselected, selections.findOne(next, id).orElseThrow());
		assertEquals(List.of(), selections.findSelectingChecklistIds(id), "a deselected photo is no longer selected");
		assertEquals(List.of(deselected), selections.findByChecklistIds(List.of(next)));
	}

	/** plan PD-8: the index of spec I-1 answers its own exception, so a racing submit can be replayed. */
	@Test
	void secondSubmittedOfACycle_isAConflict() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String first = checklists.started(sale, visit.appointmentId());
		submitted(first);
		String second = secondRevision(first);

		assertThrows(ChecklistSubmittedConflictException.class, () -> transaction().executeWithoutResult(status -> {
			SurveyChecklist checklist = checklistRows.findByIdForUpdate(SurveyChecklistId.of(second)).orElseThrow();
			checklist.submit(sale.userId(), T0);
			checklistRows.update(checklist);
		}));
	}

	/** A draft on the visit of this revision, which already left DRAFT (one draft per visit). */
	private String secondRevision(String checklistId) {
		SurveyChecklist first = checklistRows.findById(SurveyChecklistId.of(checklistId)).orElseThrow();
		SurveyChecklistId id = SurveyChecklistId.of(ulidGenerator.generateId());
		SurveyChecklist next = SurveyChecklist.start(id, first.getLeadId(), first.getChecklistNo() + 1,
				first.getVisitAppointmentId(), first.getIntentCycle(), sale.userId(), T0);
		transaction().executeWithoutResult(status -> checklistRows.insert(next));
		return id.getValue();
	}
}
