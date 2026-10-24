package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.application.command.IntakeInput;
import vn.sunvibe.survey.core.application.command.StartChecklistCommand;
import vn.sunvibe.survey.core.application.command.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.application.exception.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.ChecklistStartResult;
import vn.sunvibe.survey.core.application.query.ChecklistTakeoverResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.arrivalDate;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.intake;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.today;

/**
 * Starting and taking over a checklist (survey-checklist spec 4.5, 5.5; W16-1..W16-3, W16-15, W16-38): the effects,
 * one test per step of each check order, and the order of the lead share lock and the History entry.
 */
class SurveyChecklistCommandServiceStartTakeoverTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    // ==================== start: effects (W16-1, W16-2) ====================

    @Test
    void start_performerAsAssignee_createsTheDraftItsGroupsAndOneHistoryEntry() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);

        ChecklistStartResult result = h.commands.start(own(SALE_A), new StartChecklistCommand(visit.getId().getValue(),
                null));

        SurveyChecklist checklist = h.checklists.all().get(0);
        assertEquals(1, checklist.getChecklistNo());
        assertEquals(SALE_A, checklist.getHolderUserId());
        assertEquals(visit.getId(), checklist.getVisitAppointmentId());
        Map<ChecklistGroup, GroupState> groups = h.groups.findAll(checklist.getId());
        assertEquals(7, groups.size());
        groups.forEach((group, state) -> assertEquals(new GroupState(GroupDocument.initial(group), 0L, null, null),
                state));
        assertEquals(List.of(), h.takeovers.all());
        ChecklistActivity activity = h.leads.checklistActivities().get(0);
        assertEquals(ChecklistActivityType.CHECKLIST_STARTED, activity.type());
        assertEquals(checklist.getId().getValue(), activity.checklistId());
        assertEquals(1, activity.checklistNo());
        assertNull(activity.detailOrNull());
        assertEquals(List.of(), h.leads.moves());
        assertEquals(4L, result.lead().version());
        assertEquals(activity.occurredAt(), result.lead().lastActivityAt());
        assertEquals("CHECKLIST_STARTED", result.historyEntry().type());
        assertEquals(AccessResourceType.CHECKLIST, h.accessLogs.all().get(0).resourceTypeOrNull());
        assertEquals(checklist.getId().getValue(), result.view().checklist().getId().getValue());
        assertTrue(result.view().editability().editable());
    }

    /** spec 5.5: the lead share lock comes first, the History entry is the last write. */
    @Test
    void start_lockOrder_shareLockFirstHistoryLast() {
        h.started(LEAD, SALE_A);

        int share = h.journal.indexOf("lead:share");
        int insert = h.journal.indexOf("checklist:insert");
        int activity = h.journal.indexOf("lead:activity:CHECKLIST_STARTED");
        assertTrue(share >= 0 && share < insert, h.journal.toString());
        assertTrue(activity > h.journal.indexOf("group:insertAll"), h.journal.toString());
        assertTrue(h.journal.subList(activity, h.journal.size()).stream()
                .noneMatch(entry -> entry.contains("insert") || entry.contains("write") || entry.contains("takeover:")),
                h.journal.toString());
        assertTrue(h.journal.subList(0, share).stream().noneMatch(entry -> entry.startsWith("checklist:")
                || entry.startsWith("group:")), h.journal.toString());
    }

    @Test
    void start_byAnAssigneeWhoDidNotPerform_needsTheIntakeDeclaration() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        String id = visit.getId().getValue();

        assertPaths(() -> h.commands.start(own(SALE_B), new StartChecklistCommand(id, null)), "intake");
        assertPaths(() -> h.commands.start(own(SALE_B), new StartChecklistCommand(id,
                intake(arrivalDate(visit).minusDays(1)))), "intake.basis.confirmedOn");
        assertPaths(() -> h.commands.start(own(SALE_B), new StartChecklistCommand(id, intake(today().plusDays(1)))),
                "intake.basis.confirmedOn");

        h.commands.start(own(SALE_B), new StartChecklistCommand(id, intake(arrivalDate(visit))));

        ChecklistTakeover intake = h.takeovers.all().get(0);
        assertEquals(1, intake.takeoverNo());
        assertEquals(TakeoverKind.START_BY_OTHER, intake.kind());
        assertNull(intake.fromUserId());
        assertEquals(SALE_B, intake.toUserId());
        assertNull(intake.snapshotOrNull());
    }

    @Test
    void start_performerSendingAnIntake_isRefusedOnIntake() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);

        assertPaths(() -> h.commands.start(own(SALE_A), new StartChecklistCommand(visit.getId().getValue(),
                intake(today()))), "intake");
    }

    // ==================== start: check order (W16-3) ====================

    @Test
    void start_invalidBodyForAnInvisibleLead_is400First() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_B);

        assertPaths(() -> h.commands.start(own(SALE_A), new StartChecklistCommand(visit.getId().getValue(),
                new IntakeInput(" ", new BasisInput(today(), "gọi 0905 123 456")))), "intake.reason",
                "intake.basis.text");
        assertEquals(List.of(), h.securityEvents.all());
    }

    @Test
    void start_unknownOrHiddenAppointment_isNotFound_withAnEventOnlyForTheHiddenOne() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_B);

        assertThrows(SurveyLeadNotFoundException.class, () -> h.commands.start(own(SALE_A),
                new StartChecklistCommand("01JNOSUCHAPPOINTMENT000000", null)));
        assertThrows(SurveyLeadNotFoundException.class, () -> h.commands.start(own(SALE_A),
                new StartChecklistCommand("not-a-ulid", null)));
        assertEquals(List.of(), h.securityEvents.all());
        assertThrows(SurveyLeadNotFoundException.class, () -> h.commands.start(own(SALE_A),
                new StartChecklistCommand(visit.getId().getValue(), null)));

        SurveySecurityEvent event = h.securityEvents.all().get(0);
        assertEquals(SecurityResourceType.APPOINTMENT, event.resourceType());
        assertEquals(visit.getId().getValue(), event.resourceId());
        assertEquals(SecurityAction.START, event.action());
        assertEquals(SecurityOutcome.NOT_VISIBLE, event.outcome());
        assertEquals(SALE_A, event.userId());
        assertEquals(LEAD, event.leadId());
    }

    @Test
    void start_leaderWhoIsNotTheAssignee_is403BeforeTheState() {
        h.lead(LEAD, "ON_HOLD", SALE_A);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);

        assertThrows(AccessDeniedException.class, () -> h.commands.start(all(LEADER),
                new StartChecklistCommand(visit.getId().getValue(), null)));
        assertEquals(SecurityOutcome.NOT_ASSIGNEE, h.securityEvents.all().get(0).outcome());
    }

    @Test
    void start_stateRefusals_inTheOrderOfSpec55() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        SurveyAppointment older = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        SurveyAppointment current = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        SurveyAppointment scheduled = h.scheduled(LEAD, SALE_A);

        assertNotAllowed(older, "appointmentId", List.of());
        assertNotAllowed(scheduled, "appointmentId", List.of());

        h.lead(LEAD, "ON_HOLD", SALE_A);
        assertNotAllowed(current, "status", List.of("SURVEY_SCHEDULED"));

        h.leads.withLead("01JLEADSHRTAAAAAAAAAAAAAAA", "ON_HOLD", "SHORT_TERM_RENT", 1, SALE_A, 1L);
        SurveyAppointment shortTerm = h.visited("01JLEADSHRTAAAAAAAAAAAAAAA", SurveyIntent.SHORT_TERM_RENT, 1, SALE_A);
        assertNotAllowed(shortTerm, "intent", List.of("LONG_TERM_RENT"));
    }

    @Test
    void start_secondStart_isAlreadyStartedBeforeIneligibility() {
        h.started(LEAD, SALE_A);
        SurveyAppointment visit = h.appointments.all().get(0);
        h.directory.makeIneligible(SALE_A);

        assertThrows(ChecklistAlreadyStartedException.class, () -> h.commands.start(own(SALE_A),
                new StartChecklistCommand(visit.getId().getValue(), null)));
    }

    @Test
    void start_ineligibleAssignee_is422BeforeTheIntake() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        h.directory.makeIneligible(SALE_B);

        assertThrows(SurveyorIneligibleException.class, () -> h.commands.start(own(SALE_B),
                new StartChecklistCommand(visit.getId().getValue(), null)));
    }

    // ==================== takeover: effects (W16-15) ====================

    @Test
    void takeOver_newAssignee_becomesHolderWithACheckpointOfTheGroupsBefore() {
        ChecklistStartResult started = h.started(LEAD, SALE_A);
        SurveyChecklist checklist = started.view().checklist();
        Map<ChecklistGroup, GroupState> before = h.groups.findAll(checklist.getId());
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.appointments.all().get(0);

        ChecklistTakeoverResult result = h.commands.takeOver(own(SALE_B), new TakeOverChecklistCommand(
                checklist.getId().getValue(), 0L, "Được giao lại lead", new BasisInput(arrivalDate(visit),
                "Quản lý giao lại hôm nay")));

        SurveyChecklist after = h.checklists.all().get(0);
        assertEquals(SALE_B, after.getHolderUserId());
        assertEquals(1L, after.getVersion());
        ChecklistTakeover takeover = h.takeovers.all().get(0);
        assertEquals(TakeoverKind.TAKEOVER, takeover.kind());
        assertEquals(1, takeover.takeoverNo());
        assertEquals(SALE_A, takeover.fromUserId());
        assertEquals(SALE_B, takeover.toUserId());
        assertEquals(before, takeover.snapshotOrNull().groups());
        assertEquals(ChecklistActivityType.CHECKLIST_TAKEN_OVER, h.leads.checklistActivities().get(1).type());
        assertEquals("CHECKLIST_TAKEN_OVER", result.historyEntry().type());
        assertTrue(h.journal.indexOf("checklist:update") < h.journal.indexOf("takeover:TAKEOVER"), h.journal.toString());
        assertEquals(List.of(), h.leads.moves());
    }

    // ==================== takeover: check order ====================

    @Test
    void takeOver_readOnlyBefore422_422BeforeHolder_holderBeforeVersion_versionBeforeDate() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        String id = checklist.getId().getValue();
        LocalDate badDate = today().plusDays(1);

        h.lead(LEAD, "ON_HOLD", SALE_A);
        h.directory.makeIneligible(SALE_A);
        assertThrows(ChecklistReadOnlyException.class, () -> takeOver(SALE_A, id, 9L, badDate));

        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        assertThrows(SurveyorIneligibleException.class, () -> takeOver(SALE_A, id, 9L, badDate));

        h.directory.withEligible(SALE_A, "Sale A");
        assertEquals("holder", assertThrows(ChecklistNotAllowedException.class,
                () -> takeOver(SALE_A, id, 9L, badDate)).getField());

        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        assertEquals("expectedVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> takeOver(SALE_B, id, 9L, badDate)).getField());
        assertPaths(() -> takeOver(SALE_B, id, 0L, badDate), "basis.confirmedOn");
        assertEquals(List.of(), h.takeovers.all());
    }

    @Test
    void takeOver_bodyBeforeNotFound_andHiddenLeadIsNotFoundWithAnEvent() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        String id = checklist.getId().getValue();

        assertPaths(() -> h.commands.takeOver(own(SALE_B), new TakeOverChecklistCommand(id, null, " ",
                new BasisInput(null, "x"))), "expectedVersion", "reason", "basis.confirmedOn");
        assertThrows(vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException.class,
                () -> takeOver(SALE_B, id, 0L, today()));
        assertEquals(SecurityResourceType.CHECKLIST, h.securityEvents.all().get(0).resourceType());
        assertEquals(SecurityAction.TAKEOVER, h.securityEvents.all().get(0).action());
    }

    @Test
    void takeOver_flushConflict_answersExpectedVersion() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        h.checklists.failNextUpdate = new ObjectOptimisticLockingFailureException(SurveyChecklist.class, "x");

        assertEquals("expectedVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> takeOver(SALE_B, checklist.getId().getValue(), 0L, today())).getField());
    }

    // ==================== helpers ====================

    private ChecklistTakeoverResult takeOver(String caller, String checklistId, Long version, LocalDate basisDate) {
        return h.commands.takeOver(own(caller), new TakeOverChecklistCommand(checklistId, version,
                "Được giao lại lead", new BasisInput(basisDate, "Quản lý giao lại")));
    }

    private void assertNotAllowed(SurveyAppointment appointment, String field, List<String> allowed) {
        ChecklistNotAllowedException refused = assertThrows(ChecklistNotAllowedException.class,
                () -> h.commands.start(own(SALE_A), new StartChecklistCommand(appointment.getId().getValue(),
                        null)));
        assertEquals(field, refused.getField());
        assertEquals(allowed, refused.getAllowedValues());
    }

    private static void assertPaths(Supplier<?> call, String... paths) {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class, call::get);
        assertEquals(List.of(paths), refused.violations().stream().map(Violation::path).toList());
    }
}
