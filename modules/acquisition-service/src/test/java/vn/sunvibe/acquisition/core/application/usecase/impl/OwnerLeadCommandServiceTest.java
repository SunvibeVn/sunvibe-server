package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadIntentCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.application.exception.ConcurrentLeadModificationException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException;
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
import vn.sunvibe.acquisition.core.testsupport.RecordingLeadSurveyPort;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

    private static ChangeLeadStatusCommand statusChange(OwnerLead lead, OwnerLeadStatus to, long expectedVersion) {
        return new ChangeLeadStatusCommand(id(lead), to, expectedVersion, null, null, null, null);
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
