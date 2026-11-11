package vn.sunvibe.acquisition.core.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadsCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadIntentCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.application.exception.ConcurrentLeadModificationException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException;
import vn.sunvibe.acquisition.core.application.query.AssignLeadsResult;
import vn.sunvibe.acquisition.core.application.query.LeadNoteResult;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.domain.exception.IntentChangeNotAllowedException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.ConfirmationBasis;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;
import vn.sunvibe.acquisition.core.testsupport.InMemoryDuplicateCandidateRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadActivityRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadIntentChangeRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture;
import vn.sunvibe.acquisition.core.application.shared.external.survey.SurveyState;
import vn.sunvibe.acquisition.core.testsupport.RecordingLeadSurveyPort;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Write side of the staff lead queue (acquisition-owner-lead-queue spec 5.3 check order: body 400 -> lead visible 404
 * -> expectedVersion 409 -> state 409 -> assignee eligible 422; Q9-5..Q9-9, Q9-17; open points 2 and 3).
 */
class OwnerLeadCommandServiceTest {

    private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
    private static final LeadViewer OWN_A = new LeadViewer(SALE_A, LeadAccess.OWN);
    private static final LeadViewer OWN_B = new LeadViewer(SALE_B, LeadAccess.OWN);
    private static final LeadViewer ALL_LEADER = new LeadViewer(LEADER, LeadAccess.ALL);
    private static final String WARD = "20194";
    private static final String APPOINTMENT = "01JAPPOINTMENTAAAAAAAAAAAA";
    private static final Instant T0 = Instant.parse("2026-10-10T08:00:00Z");
    private static final Instant NEXT = Instant.parse("2026-10-20T02:00:00Z");

    private FailingLeadRepository leads;
    private InMemoryOwnerLeadActivityRepository activities;
    private InMemoryDuplicateCandidateRepository candidates;
    private CountingDirectory directory;
    private OwnerLeadCommandService service;
    private RecordingLeadSurveyPort surveys;
    private InMemoryOwnerLeadIntentChangeRepository intentChanges;
    private int minutes;

    @BeforeEach
    void setUp() {
        leads = new FailingLeadRepository();
        activities = new InMemoryOwnerLeadActivityRepository();
        candidates = new InMemoryDuplicateCandidateRepository(leads);
        directory = new CountingDirectory();
        directory.withEligible(SALE_A).withEligible(SALE_B);
        surveys = new RecordingLeadSurveyPort(leads);
        intentChanges = new InMemoryOwnerLeadIntentChangeRepository();
        IdGenerator<Long> ids = new AtomicLong(7_000L)::incrementAndGet;
        service = new OwnerLeadCommandService(leads, activities, candidates, new LeadVisibility(leads), directory,
                surveys, intentChanges, ids);
    }

    // ==================== survey hooks (survey-schedule spec 4.6, 5.5) ====================

    /** survey-schedule spec 5.5: the lead row is written before the survey hook runs. */
    @Test
    void surveyHook_runsAfterLeadSave() {
        OwnerLead lead = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        surveys.answering(APPOINTMENT, 2);

        service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_B, 0L, null));

        RecordingLeadSurveyPort.Call call = surveys.calls().get(0);
        assertEquals("reassigned", call.hook());
        assertEquals(SALE_B, call.argument());
        assertEquals(LEADER, call.actorUserId());
        assertEquals(1L, call.storedVersion());
        OwnerLeadActivity activity = activities.all().get(0);
        assertEquals(OwnerLeadActivityType.REASSIGNED, activity.getType());
        assertEquals(APPOINTMENT, activity.getReferenceId());
        assertEquals(2, activity.getReferenceNo());
        assertEquals(call.at(), activity.getOccurredAt());
    }

    @Test
    void assign_firstAssignment_callsNoHook() {
        OwnerLead lead = stored(OwnerLeadStatus.NEW, null);

        service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_A, 0L, null));

        assertEquals(List.of(), surveys.calls());
    }

    @Test
    void changeStatus_holdOrCloseFromSurveyScheduled_cancelsThroughTheHookWithTheText() {
        OwnerLead held = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        OwnerLead closed = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        surveys.answering(APPOINTMENT, 1);

        service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(held), OwnerLeadStatus.ON_HOLD, 0L, null, null,
                "  Owner abroad, call 0905 123 456  ", null));
        service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(closed), OwnerLeadStatus.REJECTED, 0L,
                "Not a fit", null, null, null));

        assertEquals("LEAD_ON_HOLD", surveys.calls().get(0).hook());
        assertEquals("Owner abroad, call 0905 123 456", surveys.calls().get(0).argument());
        assertEquals(1L, surveys.calls().get(0).storedVersion());
        assertEquals("LEAD_CLOSED", surveys.calls().get(1).hook());
        assertEquals("Not a fit", surveys.calls().get(1).argument());
        assertEquals(APPOINTMENT, activities.all().get(0).getReferenceId());
        assertEquals(OwnerLeadActivityType.STATUS_CHANGED, activities.all().get(0).getType());
    }

    @Test
    void changeStatus_holdFromContacted_callsNoHook() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);

        service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.ON_HOLD, 0L, null, null,
                null, null));

        assertEquals(List.of(), surveys.calls());
    }

    // ==================== the survey record (checklist submission spec 4.8, D3-8, D3-13) ====================

    private static final SurveyState PASSED = new SurveyState(true, true, false, false);
    private static final SurveyState HELD_PASSED = new SurveyState(true, false, false, false);
    private static final SurveyState SUBMITTED = new SurveyState(false, false, true, true);

    @Test
    void onHoldToContacted_withPassed_refused_allowedWithoutContacted() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);
        surveys.withState(HELD_PASSED);

        InvalidLeadTransitionException refused = assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 0L)));

        assertEquals(OwnerLeadStatus.CONTACTED, refused.getTo());
        assertEquals(List.of(OwnerLeadStatus.DUPLICATE, OwnerLeadStatus.REJECTED, OwnerLeadStatus.WITHDRAWN),
                refused.getAllowed());
    }

    @Test
    void onHoldToContacted_withSubmitted_refused() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);
        surveys.withState(SUBMITTED);

        assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 0L)));
    }

    /** spec 4.8: the record is read after the lead row is locked by the command, for the lead's cycle. */
    @Test
    void onHoldToContacted_asksTheRecordAfterTheLeadRowIsWritten() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);

        OwnerLeadSummary summary = service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 0L));

        assertEquals(OwnerLeadStatus.CONTACTED, summary.status());
        assertEquals(1, surveys.stateQuestions().size());
        assertEquals("1", surveys.stateQuestions().get(0).argument());
        assertEquals(1L, surveys.stateQuestions().get(0).storedVersion());
    }

    @Test
    void onHoldToClosing_withPassed_allowed() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);
        surveys.withState(PASSED);

        ChangeLeadStatusCommand close = new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.WITHDRAWN, 0L,
                "Owner withdrew", null, null, null);

        assertEquals(OwnerLeadStatus.WITHDRAWN, service.changeStatus(OWN_A, close).status());
    }

    @Test
    void intentChange_withPassed_refused_emptyAllowed() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);
        surveys.withState(HELD_PASSED);

        IntentChangeNotAllowedException refused = assertThrows(IntentChangeNotAllowedException.class,
                () -> service.changeIntent(OWN_A, intentChange(lead)));

        assertEquals(OwnerLeadStatus.ON_HOLD, refused.getStatus());
        assertEquals(List.of(), refused.getAllowed());
        assertEquals("1", surveys.stateQuestions().get(0).argument(), "the cycle that would end");
        assertEquals(List.of(), surveys.calls());
    }

    @Test
    void intentChange_qualifiedLead_refused_emptyAllowed() {
        OwnerLead lead = stored(OwnerLeadStatus.QUALIFIED, SALE_A);

        IntentChangeNotAllowedException refused = assertThrows(IntentChangeNotAllowedException.class,
                () -> service.changeIntent(OWN_A, intentChange(lead)));

        assertEquals(List.of(), refused.getAllowed());
    }

    /** spec 4.8: a requirement change with a SUBMITTED revision stays allowed and ends that revision's cycle. */
    @Test
    void intentChange_withSubmitted_allowed() {
        OwnerLead lead = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        surveys.withState(SUBMITTED);

        OwnerLeadSummary summary = service.changeIntent(OWN_A, intentChange(lead));

        assertEquals(OwnerLeadStatus.CONTACTED, summary.status());
        assertEquals(2, reload(lead).getIntentCycle());
    }

    private static ChangeLeadIntentCommand intentChange(OwnerLead lead) {
        return new ChangeLeadIntentCommand(id(lead), 0L, OwnerLeadIntent.SHORT_TERM_RENT, Optional.empty(),
                "Owner prefers holiday guests", new ConfirmationBasis(OwnerLead.vietnamToday(), "Owner said so"));
    }

    // ==================== requirement change (survey-schedule US-5) ====================

    @Test
    void changeIntent_fromSurveyScheduled_movesToContactedCancelsAndRecordsTheChange() {
        OwnerLead lead = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        surveys.answering(APPOINTMENT, 3);
        AskingPrice night = AskingPrice.of(800_000L, "VND", PriceUnit.NIGHT, OwnerLeadIntent.SHORT_TERM_RENT);

        OwnerLeadSummary summary = service.changeIntent(OWN_A, new ChangeLeadIntentCommand(id(lead), 0L,
                OwnerLeadIntent.SHORT_TERM_RENT, Optional.of(night), "  Owner prefers holiday guests ",
                new ConfirmationBasis(LocalDate.ofInstant(T0, OwnerLead.VIETNAM), " Owner said so ")));

        assertEquals(OwnerLeadStatus.CONTACTED, summary.status());
        assertEquals(OwnerLeadIntent.SHORT_TERM_RENT, summary.intent());
        assertEquals(1L, summary.version());
        OwnerLead stored = reload(lead);
        assertEquals(2, stored.getIntentCycle());
        assertEquals(Optional.of(night), stored.getAskingPrice());
        RecordingLeadSurveyPort.Call call = surveys.calls().get(0);
        assertEquals("intentChanged", call.hook());
        assertEquals("2", call.argument());
        assertEquals(1L, call.storedVersion());
        OwnerLeadIntentChange row = intentChanges.all().get(0);
        assertEquals(OwnerLeadIntent.LONG_TERM_RENT, row.getFromIntent());
        assertEquals("Owner prefers holiday guests", row.getReason());
        assertEquals("Owner said so", row.getBasis().text());
        assertEquals(SALE_A, row.getActorUserId());
        OwnerLeadActivity activity = activities.all().get(0);
        assertEquals(OwnerLeadActivityType.INTENT_CHANGED, activity.getType());
        assertEquals(OwnerLeadStatus.SURVEY_SCHEDULED, activity.getFromStatus());
        assertEquals(OwnerLeadStatus.CONTACTED, activity.getToStatus());
        assertEquals(APPOINTMENT, activity.getReferenceId());
        assertNull(activity.getNote());
        assertEquals(row.getOccurredAt(), activity.getOccurredAt());
    }

    @Test
    void changeIntent_checkOrder_bodyThenVisibilityThenVersionThenStatus() {
        OwnerLead closed = stored(OwnerLeadStatus.REJECTED, SALE_A);
        ConfirmationBasis basis = new ConfirmationBasis(LocalDate.ofInstant(T0, OwnerLead.VIETNAM), "Said so");

        assertEquals("newIntent", assertThrows(InvalidLeadCommandException.class, () -> service.changeIntent(OWN_A,
                new ChangeLeadIntentCommand("unknown", 0L, OwnerLeadIntent.SALE, Optional.empty(), "r", basis)))
                .getField());
        assertThrows(OwnerLeadNotFoundException.class, () -> service.changeIntent(OWN_B,
                new ChangeLeadIntentCommand(id(closed), 0L, OwnerLeadIntent.SHORT_TERM_RENT, Optional.empty(), "r",
                        basis)));
        assertThrows(ConcurrentLeadModificationException.class, () -> service.changeIntent(OWN_A,
                new ChangeLeadIntentCommand(id(closed), 4L, OwnerLeadIntent.SHORT_TERM_RENT, Optional.empty(), "r",
                        basis)));
        IntentChangeNotAllowedException refused = assertThrows(IntentChangeNotAllowedException.class,
                () -> service.changeIntent(OWN_A, new ChangeLeadIntentCommand(id(closed), 0L,
                        OwnerLeadIntent.SHORT_TERM_RENT, Optional.empty(), "r", basis)));
        assertEquals(OwnerLead.INTENT_CHANGE_STATUSES, refused.getAllowed());
        assertEquals(List.of(), surveys.calls());
        assertEquals(List.of(), intentChanges.all());
    }

    @Test
    void changeIntent_hookFails_nothingBeyondTheLeadSaveIsWritten() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        surveys.failNextCall = new IllegalStateException("survey down");

        assertThrows(IllegalStateException.class, () -> service.changeIntent(OWN_A, new ChangeLeadIntentCommand(
                id(lead), 0L, OwnerLeadIntent.SHORT_TERM_RENT, Optional.empty(), "r",
                new ConfirmationBasis(LocalDate.ofInstant(T0, OwnerLead.VIETNAM), "Said so"))));

        assertEquals(List.of(), intentChanges.all());
        assertEquals(List.of(), activities.all());
    }

    // ==================== assign ====================

    @Test
    void assign_newLead_becomesAssignedWithActivity() {
        OwnerLead lead = stored(OwnerLeadStatus.NEW, null);

        OwnerLeadSummary summary = service.assign(ALL_LEADER,
                new AssignLeadCommand(id(lead), SALE_A, 0L, "Please call today"));

        assertEquals(OwnerLeadStatus.ASSIGNED, summary.status());
        assertEquals(SALE_A, summary.assignedUserId());
        assertEquals(1L, summary.version());
        assertEquals(OwnerLeadStatus.ASSIGNED, reload(lead).getStatus());
        assertEquals(SALE_A, reload(lead).getAssignedUserId());
        List<OwnerLeadActivity> history = activities.all();
        assertEquals(1, history.size());
        OwnerLeadActivity activity = history.get(0);
        assertEquals(OwnerLeadActivityType.ASSIGNED, activity.getType());
        assertEquals(LEADER, activity.getActorUserId());
        assertEquals(OwnerLeadStatus.NEW, activity.getFromStatus());
        assertEquals(OwnerLeadStatus.ASSIGNED, activity.getToStatus());
        assertEquals(SALE_A, activity.getToAssigneeId());
        assertEquals("Please call today", activity.getNote());
        assertEquals(lead.getId(), activity.getLeadId());
    }

    /** acquisition-owner-lead-queue-ui C-18: the answer names the assignee, with one lookup. */
    @Test
    void assign_answerCarriesTheAssigneeNameFromOneLookup() {
        directory.withName(SALE_A, "Nguyen An");
        OwnerLead lead = stored(OwnerLeadStatus.NEW, null);

        OwnerLeadSummary summary = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_A, 0L, null));

        assertEquals("Nguyen An", summary.assignedUserName());
        assertEquals(1, directory.nameLookups);
    }

    @Test
    void assign_toIneligibleUser_throwsIneligibleAssignee() {
        OwnerLead lead = stored(OwnerLeadStatus.NEW, null);
        int saves = leads.saveCount;

        IneligibleAssigneeException ex = assertThrows(IneligibleAssigneeException.class,
                () -> service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), "01JNOBODYAAAAAAAAAAAAAAAAA", 0L,
                        null)));

        assertEquals("assigneeUserId", ex.getField());
        assertFalse(ex.getMessage().contains("01JNOBODY"));
        assertEquals(saves, leads.saveCount);
        assertTrue(activities.all().isEmpty());
        assertEquals(OwnerLeadStatus.NEW, reload(lead).getStatus());
    }

    @Test
    void assign_closedLead_throwsInvalidTransitionWithNullTo() {
        OwnerLead lead = stored(OwnerLeadStatus.REJECTED, SALE_A);

        InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                () -> service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), "01JNOBODYAAAAAAAAAAAAAAAAA", 0L,
                        null)));

        assertEquals(OwnerLeadStatus.REJECTED, ex.getFrom());
        assertNull(ex.getTo());
        assertEquals(List.of(), ex.getAllowed());
        assertEquals(0, directory.calls, "state is checked before the assignee");
        assertTrue(activities.all().isEmpty());
    }

    @Test
    void assign_staleVersion_throwsConcurrent() {
        OwnerLead lead = stored(OwnerLeadStatus.REJECTED, SALE_A);

        assertThrows(ConcurrentLeadModificationException.class,
                () -> service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_B, 4L, null)));

        assertEquals(0, directory.calls);
        assertTrue(activities.all().isEmpty());
    }

    @Test
    void assign_sameAssignee_isNoOpWithoutActivityAndVersionUnchanged() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        directory.eligible.remove(SALE_A);
        int saves = leads.saveCount;

        OwnerLeadSummary summary = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_A, 0L, "again"));

        assertEquals(0L, summary.version());
        assertEquals(SALE_A, summary.assignedUserId());
        assertEquals(OwnerLeadStatus.CONTACTED, summary.status());
        assertEquals(saves, leads.saveCount);
        assertTrue(activities.all().isEmpty());
        assertEquals(0, directory.calls, "the no-op does not ask whether the current assignee is still eligible");
    }

    /**
     * acquisition-owner-lead-queue-ui W14-2: every command answer carries the latest stored activity time, read after
     * the new activity is saved; a no-op assign returns the unchanged value.
     */
    @Test
    void commands_answerCarriesTheLatestStoredActivityTime() {
        OwnerLead lead = stored(OwnerLeadStatus.NEW, null);

        OwnerLeadSummary assigned = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_A, 0L, null));
        assertEquals(latestStored(lead), assigned.lastActivityAt());

        OwnerLeadSummary again = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_A, 1L, null));
        assertEquals(assigned.lastActivityAt(), again.lastActivityAt());
        assertEquals(1, activities.all().size());

        OwnerLeadSummary contacted = service.changeStatus(ALL_LEADER,
                new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.CONTACTED, 1L, null, null, null, null));
        assertEquals(latestStored(lead), contacted.lastActivityAt());

        LeadNoteResult noted = service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "Owner is abroad", null));
        assertEquals(noted.activity().occurredAt(), noted.lead().lastActivityAt());
        assertEquals(latestStored(lead), noted.lead().lastActivityAt());
    }

    @Test
    void assign_sameAssigneeWithoutHistory_fallsBackToCreatedAt() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);

        OwnerLeadSummary summary = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_A, 0L, null));

        assertEquals(lead.getCreatedAt(), summary.lastActivityAt());
    }

    @Test
    void assign_invisibleLead_throwsNotFoundBeforeAnyOtherCheck() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead closed = stored(OwnerLeadStatus.REJECTED, SALE_A);

        assertThrows(OwnerLeadNotFoundException.class, () -> service.assign(OWN_B,
                new AssignLeadCommand(id(lead), "01JNOBODYAAAAAAAAAAAAAAAAA", 9L, null)));
        assertThrows(OwnerLeadNotFoundException.class, () -> service.assign(OWN_B,
                new AssignLeadCommand(id(closed), SALE_B, 9L, null)));
        assertThrows(OwnerLeadNotFoundException.class, () -> service.assign(ALL_LEADER,
                new AssignLeadCommand(OwnerLeadId.generate().getValue(), SALE_B, 0L, null)));
        assertThrows(OwnerLeadNotFoundException.class, () -> service.assign(ALL_LEADER,
                new AssignLeadCommand("x", SALE_B, 0L, null)));

        assertEquals(0, directory.calls);
    }

    /** acquisition-owner-lead-queue-ui C-35: a lead the caller may not see is refused before any name is looked up. */
    @Test
    void assign_invisibleLead_makesNoNameLookup() {
        directory.withName(SALE_A, "Nguyen An");
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        assertThrows(OwnerLeadNotFoundException.class, () -> service.assign(OWN_B,
                new AssignLeadCommand(id(lead), SALE_B, 0L, null)));

        assertEquals(0, directory.nameLookups);
    }

    @Test
    void assign_blankAssignee_isBadRequestBeforeVisibility() {
        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> service.assign(OWN_B, new AssignLeadCommand(OwnerLeadId.generate().getValue(), " ", 0L, null)));

        assertEquals("assigneeUserId", ex.getField());
    }

    @Test
    void assign_afterReassignment_oldAssigneeGetsNotFound() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);

        OwnerLeadSummary summary = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_B, 0L, null));

        assertThrows(OwnerLeadNotFoundException.class, () -> service.changeStatus(OWN_A,
                statusChange(lead, OwnerLeadStatus.ON_HOLD, summary.version())));
        assertThrows(OwnerLeadNotFoundException.class,
                () -> service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "Still mine?", null)));
        LeadNoteResult result = service.addNote(OWN_B, new AddLeadNoteCommand(id(lead), "Took over", null));
        assertEquals(SALE_B, result.lead().assignedUserId());
    }

    @Test
    void assign_keepsNextActionAtAndNotes() {
        OwnerLead lead = OwnerLeadFixture.newLead(OwnerLeadStatus.NEW, null, WARD, T0);
        leads.save(lead);
        lead.assignTo(SALE_A, null);
        lead.addNote("Owner prefers mornings", NEXT);
        leads.save(lead);

        OwnerLeadSummary summary = service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_B, 1L, null));

        OwnerLead after = reload(lead);
        assertEquals(OwnerLeadStatus.ASSIGNED, after.getStatus());
        assertEquals(SALE_B, after.getAssignedUserId());
        assertEquals(NEXT, after.getNextActionAt());
        assertEquals("Owner note", after.getNotes());
        assertEquals(NEXT, summary.nextActionAt());
        assertEquals(2L, summary.version());
        OwnerLeadActivity activity = activities.all().get(0);
        assertEquals(OwnerLeadActivityType.REASSIGNED, activity.getType());
        assertEquals(SALE_A, activity.getFromAssigneeId());
        assertEquals(SALE_B, activity.getToAssigneeId());
        assertNull(activity.getFromStatus());
        assertNull(activity.getToStatus());
    }

    // ==================== assignLeads (acquisition-owner-lead-bulk-assign spec 3.1) ====================

    /**
     * acquisition-owner-lead-bulk-assign BR-7: each lead gets one outcome, checked in this order: not found (unknown,
     * not a ULID, or hidden), closed, already with this person (even with a wrong version), changed by someone else,
     * assigned. The answer follows the request order and only the assigned lead changes; the assignee is asked once.
     */
    @Test
    void assignLeads_outcomeOrder_closedThenSameAssigneeThenVersion() {
        OwnerLead closedOfB = stored(OwnerLeadStatus.REJECTED, SALE_B);
        OwnerLead heldByB = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead heldByA = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead fresh = stored(OwnerLeadStatus.NEW, null);
        String unknown = OwnerLeadId.generate().getValue();

        AssignLeadsResult result = service.assignLeads(ALL_LEADER, new AssignLeadsCommand(SALE_B, null, List.of(
                new AssignLeadsCommand.LeadInput(unknown, 0L),
                new AssignLeadsCommand.LeadInput("not-a-ulid", 0L),
                input(closedOfB, 5L), input(heldByB, 5L), input(heldByA, 5L), input(fresh, 0L))));

        assertEquals(List.of(new AssignLeadsResult.LeadOutcome(unknown, AssignLeadsResult.Outcome.NOT_FOUND),
                new AssignLeadsResult.LeadOutcome("not-a-ulid", AssignLeadsResult.Outcome.NOT_FOUND),
                new AssignLeadsResult.LeadOutcome(id(closedOfB), AssignLeadsResult.Outcome.CLOSED),
                new AssignLeadsResult.LeadOutcome(id(heldByB), AssignLeadsResult.Outcome.ALREADY_ASSIGNED),
                new AssignLeadsResult.LeadOutcome(id(heldByA), AssignLeadsResult.Outcome.STALE),
                new AssignLeadsResult.LeadOutcome(id(fresh), AssignLeadsResult.Outcome.ASSIGNED)),
                result.outcomes());
        assertEquals(1, directory.calls);
        OwnerLead assigned = reload(fresh);
        assertEquals(OwnerLeadStatus.ASSIGNED, assigned.getStatus());
        assertEquals(SALE_B, assigned.getAssignedUserId());
        assertEquals(1L, assigned.getVersion());
        assertEquals(List.of(fresh.getId()), activities.all().stream().map(OwnerLeadActivity::getLeadId).toList());
        assertEquals(OwnerLeadActivityType.ASSIGNED, activities.all().get(0).getType());
        for (OwnerLead unchanged : List.of(closedOfB, heldByB, heldByA)) {
            assertEquals(0L, reload(unchanged).getVersion());
            assertEquals(unchanged.getStatus(), reload(unchanged).getStatus());
            assertEquals(unchanged.getAssignedUserId(), reload(unchanged).getAssignedUserId());
        }

        // acquisition-owner-lead-queue BR-2: a lead the viewer may not see is NOT_FOUND, like an unknown one
        OwnerLead hidden = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        AssignLeadsResult hiddenResult = service.assignLeads(OWN_A,
                new AssignLeadsCommand(SALE_A, null, List.of(input(hidden, 0L))));

        assertEquals(List.of(new AssignLeadsResult.LeadOutcome(id(hidden), AssignLeadsResult.Outcome.NOT_FOUND)),
                hiddenResult.outcomes());
        assertEquals(SALE_B, reload(hidden).getAssignedUserId());
        assertEquals(0L, reload(hidden).getVersion());
    }

    /**
     * acquisition-owner-lead-bulk-assign BR-9: an ineligible assignee refuses the whole action, also when every lead is
     * already theirs, and a note too long refuses it before the assignee is asked; no lead is read or locked.
     */
    @Test
    void assignLeads_ineligibleAssignee_throwsBeforeAnyLeadIsRead() {
        String nobody = "01JNOBODYAAAAAAAAAAAAAAAAA";
        OwnerLead first = stored(OwnerLeadStatus.ASSIGNED, nobody);
        OwnerLead second = stored(OwnerLeadStatus.CONTACTED, nobody);
        int saves = leads.saveCount;

        IneligibleAssigneeException refused = assertThrows(IneligibleAssigneeException.class,
                () -> service.assignLeads(ALL_LEADER, new AssignLeadsCommand(nobody, null,
                        List.of(input(first, 0L), input(second, 0L)))));

        assertEquals("assigneeUserId", refused.getField());
        assertEquals(List.of(), leads.updateLocks());
        assertEquals(saves, leads.saveCount);
        assertEquals(List.of(), activities.all());

        int asked = directory.calls;
        InvalidLeadCommandException tooLong = assertThrows(InvalidLeadCommandException.class,
                () -> service.assignLeads(ALL_LEADER, new AssignLeadsCommand(SALE_B,
                        "  " + "n".repeat(2001) + "  ", List.of(input(first, 0L)))));

        assertEquals("note", tooLong.getField());
        assertEquals(List.of(), leads.updateLocks());
        assertEquals(asked, directory.calls);
        assertEquals(saves, leads.saveCount);
    }

    /**
     * acquisition-owner-lead-bulk-assign BR-4: a reassigned lead moves its scheduled survey through the hook after its
     * row is written, and its History entry names the appointment; a first assignment calls no hook.
     */
    @Test
    void assignLeads_reassignedLead_callsTheSurveyHookAndNamesTheAppointment() {
        OwnerLead scheduled = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        OwnerLead fresh = stored(OwnerLeadStatus.NEW, null);
        surveys.answering(APPOINTMENT, 2);

        service.assignLeads(ALL_LEADER, new AssignLeadsCommand(SALE_B, null,
                List.of(input(scheduled, 0L), input(fresh, 0L))));

        assertEquals(1, surveys.calls().size());
        RecordingLeadSurveyPort.Call call = surveys.calls().get(0);
        assertEquals("reassigned", call.hook());
        assertEquals(id(scheduled), call.leadId());
        assertEquals(SALE_B, call.argument());
        assertEquals(LEADER, call.actorUserId());
        assertEquals(1L, call.storedVersion());
        OwnerLeadActivity reassigned = activityOf(scheduled);
        assertEquals(OwnerLeadActivityType.REASSIGNED, reassigned.getType());
        assertEquals(SALE_A, reassigned.getFromAssigneeId());
        assertEquals(SALE_B, reassigned.getToAssigneeId());
        assertEquals(APPOINTMENT, reassigned.getReferenceId());
        assertEquals(2, reassigned.getReferenceNo());
        assertEquals(call.at(), reassigned.getOccurredAt());
        OwnerLeadActivity assigned = activityOf(fresh);
        assertEquals(OwnerLeadActivityType.ASSIGNED, assigned.getType());
        assertNull(assigned.getReferenceId());
        assertNull(assigned.getReferenceNo());
    }

    /**
     * acquisition-owner-lead-bulk-assign D-2: the leads are locked in ascending id order (Java String order) whatever the
     * request order, so two bulk commands never wait on each other in a cycle; the answer keeps the request order.
     */
    @Test
    void assignLeads_locksInAscendingIdOrder_answersInRequestOrder() {
        List<OwnerLead> sorted = new ArrayList<>(List.of(stored(OwnerLeadStatus.NEW, null),
                stored(OwnerLeadStatus.NEW, null), stored(OwnerLeadStatus.NEW, null)));
        sorted.sort(Comparator.comparing(OwnerLeadCommandServiceTest::id));
        List<OwnerLead> request = List.of(sorted.get(1), sorted.get(2), sorted.get(0));

        AssignLeadsResult result = service.assignLeads(ALL_LEADER, new AssignLeadsCommand(SALE_A, null,
                request.stream().map(lead -> input(lead, 0L)).toList()));

        assertEquals(sorted.stream().map(OwnerLead::getId).toList(), leads.updateLocks());
        assertEquals(request.stream().map(OwnerLeadCommandServiceTest::id).toList(),
                result.outcomes().stream().map(AssignLeadsResult.LeadOutcome::leadId).toList());
        assertEquals(List.of(AssignLeadsResult.Outcome.ASSIGNED, AssignLeadsResult.Outcome.ASSIGNED,
                        AssignLeadsResult.Outcome.ASSIGNED),
                result.outcomes().stream().map(AssignLeadsResult.LeadOutcome::outcome).toList());
    }

    /**
     * acquisition-owner-lead-bulk-assign BR-6: the one note, cleaned like the note of one assignment, is on the History
     * entry of every assigned lead; a lead that is unchanged or not assigned gets no entry; a blank note is no note.
     */
    @Test
    void assignLeads_note_onEveryAssignedLeadOnly() {
        OwnerLead fresh = stored(OwnerLeadStatus.NEW, null);
        OwnerLead contacted = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        OwnerLead heldByB = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead changed = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        AssignLeadsResult result = service.assignLeads(ALL_LEADER, new AssignLeadsCommand(SALE_B, "  Chuyển lead  ",
                List.of(input(fresh, 0L), input(contacted, 0L), input(heldByB, 0L), input(changed, 3L))));

        assertEquals(List.of(AssignLeadsResult.Outcome.ASSIGNED, AssignLeadsResult.Outcome.ASSIGNED,
                        AssignLeadsResult.Outcome.ALREADY_ASSIGNED, AssignLeadsResult.Outcome.STALE),
                result.outcomes().stream().map(AssignLeadsResult.LeadOutcome::outcome).toList());
        assertEquals(2, activities.all().size());
        assertEquals("Chuyển lead", activityOf(fresh).getNote());
        assertEquals("Chuyển lead", activityOf(contacted).getNote());

        OwnerLead blank = stored(OwnerLeadStatus.NEW, null);
        service.assignLeads(ALL_LEADER, new AssignLeadsCommand(SALE_A, "   ", List.of(input(blank, 0L))));

        assertNull(activityOf(blank).getNote());
    }

    // ==================== changeStatus ====================

    @Test
    void changeStatus_allowedTransition_savesStatusChangedActivityWithActor() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        OwnerLeadSummary summary = service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(lead),
                OwnerLeadStatus.CONTACTED, 0L, null, null, "Owner answered", NEXT));

        assertEquals(OwnerLeadStatus.CONTACTED, summary.status());
        assertEquals(1L, summary.version());
        assertEquals(NEXT, summary.nextActionAt());
        assertEquals(OwnerLeadStatus.CONTACTED, reload(lead).getStatus());
        OwnerLeadActivity activity = activities.all().get(0);
        assertEquals(OwnerLeadActivityType.STATUS_CHANGED, activity.getType());
        assertEquals(SALE_A, activity.getActorUserId());
        assertEquals(OwnerLeadStatus.ASSIGNED, activity.getFromStatus());
        assertEquals(OwnerLeadStatus.CONTACTED, activity.getToStatus());
        assertEquals("Owner answered", activity.getNote());
        assertEquals(NEXT, activity.getNextActionAt());
    }

    /** acquisition-owner-lead-queue-ui C-18: the status-change answer names the assignee with one lookup. */
    @Test
    void changeStatus_answerCarriesNameFromOneLookup() {
        directory.withName(SALE_A, "Nguyen An");
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        OwnerLeadSummary summary = service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 0L));

        assertEquals("Nguyen An", summary.assignedUserName());
        assertEquals(1, directory.nameLookups);
    }

    @Test
    void changeStatus_forbiddenTransition_throwsWithAllowedStates() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);

        InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.ASSIGNED, 0L)));

        assertEquals(OwnerLeadStatus.CONTACTED, ex.getFrom());
        assertEquals(OwnerLeadStatus.ASSIGNED, ex.getTo());
        assertEquals(List.of(OwnerLeadStatus.ON_HOLD, OwnerLeadStatus.DUPLICATE, OwnerLeadStatus.REJECTED,
                OwnerLeadStatus.WITHDRAWN), ex.getAllowed());
        assertTrue(activities.all().isEmpty());
        assertEquals(0L, reload(lead).getVersion());
    }

    @Test
    void changeStatus_closedLead_throwsWithEmptyAllowed() {
        OwnerLead lead = stored(OwnerLeadStatus.WITHDRAWN, SALE_A);

        InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 0L)));

        assertEquals(List.of(), ex.getAllowed());
        assertTrue(activities.all().isEmpty());
    }

    @Test
    void changeStatus_staleVersionBeatsInvalidTransition() {
        OwnerLead lead = stored(OwnerLeadStatus.WITHDRAWN, SALE_A);

        assertThrows(ConcurrentLeadModificationException.class,
                () -> service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 5L)));
    }

    @Test
    void changeStatus_invisibleLeadWithValidBody_isNotFound() {
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead unassigned = stored(OwnerLeadStatus.NEW, null);

        assertThrows(OwnerLeadNotFoundException.class,
                () -> service.changeStatus(OWN_A, statusChange(others, OwnerLeadStatus.CONTACTED, 0L)));
        assertThrows(OwnerLeadNotFoundException.class, () -> service.changeStatus(OWN_A,
                new ChangeLeadStatusCommand(id(unassigned), OwnerLeadStatus.REJECTED, 99L, "Not real", null, null,
                        null)));
        assertTrue(activities.all().isEmpty());
    }

    @Test
    void changeStatus_closeWithoutReason_isBadRequestBeforeVisibility() {
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B);

        InvalidLeadCommandException blank = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(others), OwnerLeadStatus.REJECTED,
                        0L, "   ", null, null, null)));
        InvalidLeadCommandException missing = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(OwnerLeadId.generate().getValue(),
                        OwnerLeadStatus.WITHDRAWN, 0L, null, null, null, null)));
        InvalidLeadCommandException noTarget = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(others), OwnerLeadStatus.DUPLICATE,
                        0L, "Same owner", null, null, null)));
        InvalidLeadCommandException noStatus = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(others), null, 0L, null, null,
                        null, null)));
        InvalidLeadCommandException blankNote = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(others), OwnerLeadStatus.CONTACTED,
                        0L, null, null, "  ", null)));

        assertEquals("reason", blank.getField());
        assertEquals("reason", missing.getField());
        assertEquals("duplicateOfLeadId", noTarget.getField());
        assertEquals("toStatus", noStatus.getField());
        assertEquals("note", blankNote.getField());
    }

    @Test
    void changeStatus_duplicateOfSelfUnknownInvisibleOrDuplicateStatus_allThrowTheSameMessage() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead alreadyDuplicate = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        alreadyDuplicate.changeStatus(OwnerLeadStatus.DUPLICATE, "Same owner", original.getId(), null, null);
        leads.save(alreadyDuplicate);

        List<String> targets = List.of(id(lead), OwnerLeadId.generate().getValue(), "not-a-ulid", id(others),
                id(alreadyDuplicate), " ");
        List<String> messages = new ArrayList<>();
        for (String target : targets) {
            InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                    () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.DUPLICATE,
                            0L, "Same owner", target, null, null)), target);
            assertEquals("duplicateOfLeadId", ex.getField(), target);
            assertFalse(ex.getMessage().contains(id(others)), target);
            messages.add(ex.getMessage());
        }
        InvalidLeadCommandException missing = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.DUPLICATE,
                        0L, "Same owner", null, null, null)));
        messages.add(missing.getMessage());

        assertEquals(1, messages.stream().distinct().count(), messages.toString());
        assertEquals(OwnerLeadStatus.ASSIGNED, reload(lead).getStatus());
        assertTrue(activities.all().isEmpty());
    }

    @Test
    void changeStatus_duplicateOfInvisibleTarget_isBadRequestEvenWhenTheLeadIsInvisible() {
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead othersToo = stored(OwnerLeadStatus.ASSIGNED, SALE_B);

        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(others), OwnerLeadStatus.DUPLICATE,
                        0L, "Same owner", id(othersToo), null, null)));

        assertEquals("duplicateOfLeadId", ex.getField());
    }

    @Test
    void changeStatus_duplicateOfVisibleOpenLead_closesAndStoresIt() {
        OwnerLead original = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        OwnerLead lead = OwnerLeadFixture.newLead(OwnerLeadStatus.NEW, null, WARD, T0.plusSeconds(3600));
        leads.save(lead);
        lead.assignTo(SALE_A, null);
        lead.addNote("Call back", NEXT);
        leads.save(lead);

        OwnerLeadSummary summary = service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(lead),
                OwnerLeadStatus.DUPLICATE, 1L, " Same  owner ", id(original), null, null));

        OwnerLead after = reload(lead);
        assertEquals(OwnerLeadStatus.DUPLICATE, after.getStatus());
        assertEquals(original.getId(), after.getDuplicateOfLeadId());
        assertEquals("Same owner", after.getCloseReason());
        assertNull(after.getNextActionAt());
        assertNull(summary.nextActionAt());
        assertEquals(2L, summary.version());
        OwnerLeadActivity activity = activities.all().get(0);
        assertEquals(OwnerLeadStatus.ASSIGNED, activity.getFromStatus());
        assertEquals(OwnerLeadStatus.DUPLICATE, activity.getToStatus());
        assertEquals(SALE_A, activity.getActorUserId());
    }

    @Test
    void changeStatus_duplicateOfAClosedButNotDuplicateLead_isAccepted() {
        OwnerLead rejected = stored(OwnerLeadStatus.REJECTED, null);
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        OwnerLeadSummary summary = service.changeStatus(ALL_LEADER, new ChangeLeadStatusCommand(id(lead),
                OwnerLeadStatus.DUPLICATE, 0L, "Same owner", id(rejected), null, null));

        assertEquals(OwnerLeadStatus.DUPLICATE, summary.status());
        assertEquals(rejected.getId(), reload(lead).getDuplicateOfLeadId());
    }

    /**
     * acquisition-owner-lead-queue BR-2, spec 5.3: a DUPLICATE close of a lead the viewer cannot see is the 404 of an
     * unknown lead, also when the target is one the viewer sees; nothing is written.
     */
    @Test
    void changeStatus_duplicateOfVisibleTargetOnInvisibleLead_isNotFound() {
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead target = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        assertThrows(OwnerLeadNotFoundException.class,
                () -> service.changeStatus(OWN_A, closeAsDuplicate(others, target, 0L)));

        assertEquals(OwnerLeadStatus.ASSIGNED, reload(others).getStatus());
        assertEquals(0L, reload(others).getVersion());
        assertTrue(activities.all().isEmpty());
    }

    /** acquisition-owner-lead-queue BR-9: a DUPLICATE close on an outdated version is refused on expectedVersion. */
    @Test
    void changeStatus_duplicateWithStaleVersion_isConflict() {
        OwnerLead target = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        ConcurrentLeadModificationException ex = assertThrows(ConcurrentLeadModificationException.class,
                () -> service.changeStatus(OWN_A, closeAsDuplicate(lead, target, 5L)));

        assertEquals("expectedVersion", ex.getField());
        assertEquals(OwnerLeadStatus.ASSIGNED, reload(lead).getStatus());
        assertEquals(0L, reload(lead).getVersion());
        assertTrue(activities.all().isEmpty());
    }

    /**
     * survey-schedule BR-3: closing a SURVEY_SCHEDULED lead as a duplicate cancels its appointment through the hook,
     * after the lead row is saved, with the close reason; the History entry names the appointment.
     */
    @Test
    void changeStatus_duplicateFromSurveyScheduled_cancelsThroughTheHookWithTheReason() {
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead lead = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        surveys.answering(APPOINTMENT, 1);

        service.changeStatus(OWN_A, closeAsDuplicate(lead, original, 0L));

        assertEquals(1, surveys.calls().size());
        RecordingLeadSurveyPort.Call call = surveys.calls().get(0);
        assertEquals("LEAD_CLOSED", call.hook());
        assertEquals(id(lead), call.leadId());
        assertEquals("Same owner", call.argument());
        assertEquals(1L, call.storedVersion());
        OwnerLeadActivity activity = activityOf(lead);
        assertEquals(APPOINTMENT, activity.getReferenceId());
        assertEquals(OwnerLeadStatus.DUPLICATE, activity.getToStatus());
        assertEquals(original.getId(), reload(lead).getDuplicateOfLeadId());
    }

    /**
     * acquisition-owner-lead-queue spec 5.3, C-17: a DUPLICATE close of a lead that another lead names as its
     * original keeps the check order. Each close below has two faults (one of them the stale version 7): a bad
     * target is answered before the lead, the lead before the version, and the version before the lead's state.
     */
    @Test
    void changeStatus_leadNamedAsOriginal_answersTheTargetTheLeadAndTheVersionFirst() {
        OwnerLead first = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        OwnerLead ownTarget = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead target = stored(OwnerLeadStatus.ASSIGNED, SALE_B);
        service.changeStatus(ALL_LEADER, closeAsDuplicate(first, original, 0L));

        InvalidLeadCommandException duplicateTarget = assertThrows(InvalidLeadCommandException.class,
                () -> service.changeStatus(ALL_LEADER, closeAsDuplicate(original, first, 7L)));
        assertThrows(OwnerLeadNotFoundException.class,
                () -> service.changeStatus(OWN_A, closeAsDuplicate(original, ownTarget, 7L)));
        ConcurrentLeadModificationException stale = assertThrows(ConcurrentLeadModificationException.class,
                () -> service.changeStatus(ALL_LEADER, closeAsDuplicate(original, target, 7L)));

        assertEquals("duplicateOfLeadId", duplicateTarget.getField());
        assertEquals("expectedVersion", stale.getField());
        assertEquals(OwnerLeadStatus.ASSIGNED, reload(original).getStatus());
        assertEquals(0L, reload(original).getVersion());
    }

    /**
     * acquisition-owner-lead-queue C-17, acquisition BR-3: a lead that other leads name as their original still takes
     * more duplicates and still closes otherwise; closed as REJECTED it is still a valid original, because only a
     * DUPLICATE target is refused.
     */
    @Test
    void changeStatus_leadNamedAsOriginal_stillTakesDuplicatesAndOtherMoves() {
        OwnerLead first = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead second = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        service.changeStatus(ALL_LEADER, closeAsDuplicate(first, original, 0L));
        service.changeStatus(ALL_LEADER, closeAsDuplicate(second, original, 0L));
        OwnerLeadSummary rejected = service.changeStatus(ALL_LEADER, new ChangeLeadStatusCommand(id(original),
                OwnerLeadStatus.REJECTED, 0L, "Not a fit", null, null, null));
        OwnerLead third = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        service.changeStatus(ALL_LEADER, closeAsDuplicate(third, original, 0L));

        assertEquals(original.getId(), reload(first).getDuplicateOfLeadId());
        assertEquals(original.getId(), reload(second).getDuplicateOfLeadId());
        assertEquals(OwnerLeadStatus.REJECTED, rejected.status());
        assertEquals(OwnerLeadStatus.DUPLICATE, reload(third).getStatus());
        assertEquals(original.getId(), reload(third).getDuplicateOfLeadId());
    }

    /** SV-003: only a DUPLICATE close reads rows with a lock; every other status change takes none. */
    @Test
    void changeStatus_otherMoves_takeNoRowLock() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 0L));
        service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.ON_HOLD, 1L));
        service.changeStatus(OWN_A, new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.REJECTED, 2L, "Not a fit",
                null, null, null));

        assertEquals(OwnerLeadStatus.REJECTED, reload(lead).getStatus());
        assertEquals(List.of(), leads.lockLog());
    }

    /**
     * [DEL-8] SV003-D2, DC-12: a forbidden move of a lead that another lead names as its original lists what the same
     * move of a lead nobody names lists, DUPLICATE included, so a refusal that changes nothing tells nothing about
     * duplicates.
     */
    @Test
    void changeStatus_forbiddenMoveOfAnOriginal_listsLikeAnyLead() {
        OwnerLead first = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead plain = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        service.changeStatus(ALL_LEADER, closeAsDuplicate(first, original, 0L));

        InvalidLeadTransitionException onOriginal = assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(OWN_A, statusChange(original, OwnerLeadStatus.NEW, 0L)));
        InvalidLeadTransitionException onPlain = assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(OWN_A, statusChange(plain, OwnerLeadStatus.NEW, 0L)));

        List<OwnerLeadStatus> openMoves = List.of(OwnerLeadStatus.CONTACTED, OwnerLeadStatus.ON_HOLD,
                OwnerLeadStatus.DUPLICATE, OwnerLeadStatus.REJECTED, OwnerLeadStatus.WITHDRAWN);
        assertEquals(OwnerLeadStatus.NEW, onOriginal.getTo());
        assertEquals(openMoves, onOriginal.getAllowed());
        assertEquals(OwnerLeadStatus.NEW, onPlain.getTo());
        assertEquals(openMoves, onPlain.getAllowed());
        assertEquals(OwnerLeadStatus.ASSIGNED, reload(original).getStatus());
        assertEquals(0L, reload(original).getVersion());
        assertNull(reload(original).getDuplicateOfLeadId());
    }

    /**
     * acquisition-owner-lead-queue C-17 on both ends of the link, [DEL-8] SV003-D1, D2 (DC-1): a lead that another
     * lead names as its original is not closed as a duplicate; the refusal lists its other statuses, names no other
     * lead, and nothing is written.
     */
    @Test
    void changeStatus_leadNamedAsOriginal_isRefusedWithTheOtherStatuses() {
        OwnerLead first = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead other = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        service.changeStatus(ALL_LEADER, closeAsDuplicate(first, original, 0L));
        int saves = leads.saveCount;
        int entries = activities.all().size();

        InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                () -> service.changeStatus(ALL_LEADER, closeAsDuplicate(original, other, 0L)));

        assertEquals(OwnerLeadStatus.ASSIGNED, ex.getFrom());
        assertEquals(OwnerLeadStatus.DUPLICATE, ex.getTo());
        assertEquals(List.of(OwnerLeadStatus.CONTACTED, OwnerLeadStatus.ON_HOLD, OwnerLeadStatus.REJECTED,
                OwnerLeadStatus.WITHDRAWN), ex.getAllowed());
        assertFalse(ex.getMessage().contains(id(first)));
        OwnerLead after = reload(original);
        assertEquals(OwnerLeadStatus.ASSIGNED, after.getStatus());
        assertNull(after.getDuplicateOfLeadId());
        assertEquals(0L, after.getVersion());
        assertEquals(saves, leads.saveCount);
        assertEquals(entries, activities.all().size());
    }

    // ==================== addNote ====================

    @Test
    void addNote_withoutNextAction_savesActivityAndLeavesLeadVersion() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        int saves = leads.saveCount;

        LeadNoteResult result = service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "  Owner is abroad  ", null));

        assertEquals(saves, leads.saveCount, "a note without a next action does not write the lead row");
        assertEquals(0L, reload(lead).getVersion());
        assertEquals(0L, result.lead().version());
        assertEquals(OwnerLeadActivityType.NOTE_ADDED, result.activity().type());
        assertEquals("Owner is abroad", result.activity().note());
        assertEquals(SALE_A, result.activity().actorUserId());
        assertNull(result.activity().nextActionAt());
        assertNotNull(result.activity().occurredAt());
        OwnerLeadActivity stored = activities.all().get(0);
        assertEquals(OwnerLeadActivityType.NOTE_ADDED, stored.getType());
        assertEquals(SALE_A, stored.getActorUserId());
        assertEquals(lead.getId(), stored.getLeadId());
    }

    /** The note answer names the actor and the assignee with one lookup; an unknown id gives a null name. */
    @Test
    void addNote_answerCarriesActorAndAssigneeNamesFromOneLookup() {
        directory.withName(SALE_A, "Nguyen An");
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        LeadNoteResult result = service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "Called", null));

        assertEquals("Nguyen An", result.activity().actorName());
        assertNull(result.activity().fromAssigneeName());
        assertNull(result.activity().toAssigneeName());
        assertEquals("Nguyen An", result.lead().assignedUserName());
        assertEquals(1, directory.nameLookups);
    }

    @Test
    void addNote_withNextAction_updatesLeadAndBumpsVersion() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);

        LeadNoteResult result = service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "Visit planned", NEXT));

        assertEquals(1L, reload(lead).getVersion());
        assertEquals(NEXT, reload(lead).getNextActionAt());
        assertEquals(1L, result.lead().version());
        assertEquals(NEXT, result.lead().nextActionAt());
        assertEquals(NEXT, result.activity().nextActionAt());
        assertEquals(1, activities.all().size());
    }

    @Test
    void addNote_closedLead_throws() {
        OwnerLead lead = stored(OwnerLeadStatus.REJECTED, SALE_A);

        InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                () -> service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "Too late", NEXT)));

        assertNull(ex.getTo());
        assertEquals(List.of(), ex.getAllowed());
        assertTrue(activities.all().isEmpty());
        assertEquals(0L, reload(lead).getVersion());
    }

    @Test
    void addNote_blankNote_isBadRequestBeforeVisibility() {
        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> service.addNote(OWN_A, new AddLeadNoteCommand(OwnerLeadId.generate().getValue(), " ", null)));

        assertEquals("note", ex.getField());
    }

    /**
     * acquisition-owner-lead-queue BR-9: a note with a next action time saves the lead, and that save can lose to a
     * change made at the same time. The note request has no expectedVersion, so the conflict names no field.
     */
    @Test
    void addNote_nextActionSaveLosesToAConcurrentChange_throwsConcurrentWithoutField() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        leads.failNextSave = true;

        ConcurrentLeadModificationException ex = assertThrows(ConcurrentLeadModificationException.class,
                () -> service.addNote(OWN_A, new AddLeadNoteCommand(id(lead), "Visit planned", NEXT)));

        assertNull(ex.getField());
        assertTrue(activities.all().isEmpty());
        assertNull(reload(lead).getNextActionAt());
    }

    @Test
    void assignAndChangeStatus_staleVersion_nameExpectedVersion() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        assertEquals("expectedVersion", assertThrows(ConcurrentLeadModificationException.class,
                () -> service.assign(ALL_LEADER, new AssignLeadCommand(id(lead), SALE_B, 3L, null))).getField());
        assertEquals("expectedVersion", assertThrows(ConcurrentLeadModificationException.class,
                () -> service.changeStatus(OWN_A, statusChange(lead, OwnerLeadStatus.CONTACTED, 3L))).getField());
    }

    @Test
    void commands_toString_doNotPrintStaffText() {
        assertFalse(new AssignLeadCommand("id", SALE_A, 0L, "secret-note").toString().contains("secret-note"));
        assertFalse(new ChangeLeadStatusCommand("id", OwnerLeadStatus.REJECTED, 0L, "secret-reason", null,
                "secret-note", null).toString().contains("secret"));
        assertFalse(new AddLeadNoteCommand("id", "secret-note", null).toString().contains("secret-note"));
    }

    // ==================== helpers ====================

    /** A lead stored in the fake repository (version 0), one minute newer than the previous one. */
    private OwnerLead stored(OwnerLeadStatus status, String assignee) {
        OwnerLead lead = OwnerLeadFixture.newLead(status, assignee, WARD, T0.plusSeconds(60L * minutes++));
        leads.save(lead);
        return lead;
    }

    private Instant latestStored(OwnerLead lead) {
        return activities.all().stream()
                .filter(activity -> activity.getLeadId().equals(lead.getId()))
                .map(OwnerLeadActivity::getOccurredAt)
                .max(Instant::compareTo)
                .orElseThrow();
    }

    private OwnerLead reload(OwnerLead lead) {
        return leads.findById(lead.getId()).orElseThrow();
    }

    private static String id(OwnerLead lead) {
        return lead.getId().getValue();
    }

    private static AssignLeadsCommand.LeadInput input(OwnerLead lead, long expectedVersion) {
        return new AssignLeadsCommand.LeadInput(id(lead), expectedVersion);
    }

    /** The one History entry of this lead. */
    private OwnerLeadActivity activityOf(OwnerLead lead) {
        List<OwnerLeadActivity> entries = activities.all().stream()
                .filter(activity -> activity.getLeadId().equals(lead.getId()))
                .toList();
        assertEquals(1, entries.size(), "one History entry per lead");
        return entries.get(0);
    }

    private static ChangeLeadStatusCommand statusChange(OwnerLead lead, OwnerLeadStatus to, long expectedVersion) {
        return new ChangeLeadStatusCommand(id(lead), to, expectedVersion, null, null, null, null);
    }

    /** Close the lead as a duplicate of the original, with the reason "Same owner". */
    private static ChangeLeadStatusCommand closeAsDuplicate(OwnerLead lead, OwnerLead original, long expectedVersion) {
        return new ChangeLeadStatusCommand(id(lead), OwnerLeadStatus.DUPLICATE, expectedVersion, "Same owner",
                id(original), null, null);
    }

    /** The lead fake, able to lose the next save to a change made at the same time. */
    private static final class FailingLeadRepository extends InMemoryOwnerLeadRepository {

        private boolean failNextSave;

        @Override
        public void save(OwnerLead lead) {
            if (failNextSave) {
                failNextSave = false;
                throw new ObjectOptimisticLockingFailureException(OwnerLead.class, lead.getId().getValue());
            }
            super.save(lead);
        }
    }

    /** Fake of the assignee port that also counts how often it was asked. */
    private static final class CountingDirectory implements LeadAssigneeDirectoryPort {

        private final Set<String> eligible = new HashSet<>();
        private final Map<String, String> names = new HashMap<>();
        private int calls;
        private int nameLookups;

        CountingDirectory withName(String userId, String fullName) {
            names.put(userId, fullName);
            return this;
        }

        CountingDirectory withEligible(String userId) {
            eligible.add(userId);
            return this;
        }

        @Override
        public boolean isEligibleAssignee(String userId) {
            calls++;
            return eligible.contains(userId);
        }

        @Override
        public List<EligibleAssignee> listEligibleAssignees() {
            return List.of();
        }

        @Override
        public Map<String, String> findDisplayNames(Set<String> userIds) {
            nameLookups++;
            Map<String, String> found = new HashMap<>();
            userIds.stream().filter(names::containsKey).forEach(id -> found.put(id, names.get(id)));
            return found;
        }
    }
}
