package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.query.AppointmentView;
import vn.sunvibe.survey.core.application.query.BookingBlock;
import vn.sunvibe.survey.core.application.query.BookingState;
import vn.sunvibe.survey.core.application.query.ChecklistSummaryView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.application.query.SurveyRecordView;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;
import vn.sunvibe.survey.core.domain.policy.checklist.MissingItem;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadOnlyReason;
import vn.sunvibe.survey.core.domain.policy.checklist.ResumeTarget;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStart;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStartBlock;
import vn.sunvibe.survey.core.domain.policy.checklist.StartBlock;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.OTHER_LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;

/**
 * The checklist parts of the survey block (survey-checklist spec 7.2, 4.10, A-10; W16-19, W16-21): whether the viewer
 * can start one on the current visit, each VISITED appointment's checklists, the LEAD security event.
 */
class SurveyBlockChecklistTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    @Test
    void block_performerAssignee_canStartOnTheCurrentVisit() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);

        SurveyBlock block = h.surveys.block(own(SALE_A), LEAD);

        assertEquals(new ChecklistStart(true, visit.getId().getValue(), false, null), block.checklistStart());
    }

    @Test
    void block_checklistStart_isBlockedPerViewerInTheOrderOfSpec72() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        assertEquals(StartBlock.NO_CURRENT_VISIT, h.surveys.block(own(SALE_A), LEAD).checklistStart()
                .blockedByOrNull());

        h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        assertEquals(StartBlock.NOT_ASSIGNEE, h.surveys.block(all(LEADER), LEAD).checklistStart().blockedByOrNull());

        h.commands.start(own(SALE_A), new vn.sunvibe.survey.core.application.command.StartChecklistCommand(
                h.appointments.all().get(0).getId().getValue(), null));
        assertEquals(StartBlock.CHECKLIST_EXISTS, h.surveys.block(own(SALE_A), LEAD).checklistStart()
                .blockedByOrNull());
    }

    // ==================== checklist submission spec 7.2 ====================

    private SurveyChecklist submitted() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        h.submitted(checklist.getId(), SALE_A);
        return checklist;
    }

    private SurveyChecklist returned(NeedsChangesKind kind) {
        SurveyChecklist checklist = submitted();
        h.decided(checklist.getId(), DecisionOutcome.NEEDS_CHANGES, kind, null, LEADER);
        return checklist;
    }

    private SurveyChecklist passed() {
        SurveyChecklist checklist = submitted();
        h.evidence.saveAll(checklist.getId(), List.of());
        h.decided(checklist.getId(), DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "QUALIFIED", SALE_A);
        return checklist;
    }

    @Test
    void block_checklistStartOffWhenRevisionExists() {
        returned(NeedsChangesKind.DOCUMENTS_ONLY);

        assertEquals(ChecklistStart.blocked(StartBlock.CHECKLIST_EXISTS), h.surveys.block(own(SALE_A), LEAD)
                .checklistStart());
    }

    @Test
    void block_revisionStartIntakeRequired() {
        SurveyChecklist checklist = returned(NeedsChangesKind.DOCUMENTS_ONLY);
        String id = checklist.getId().getValue();

        assertEquals(new RevisionStart(true, id, false, null), h.surveys.block(own(SALE_A), LEAD).revisionStart());

        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);

        assertEquals(new RevisionStart(true, id, true, null), h.surveys.block(own(SALE_B), LEAD).revisionStart());
    }

    @Test
    void block_revisionStart_blockedInOrder() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        assertEquals(RevisionStartBlock.NO_CURRENT_VISIT, h.surveys.block(own(SALE_A), LEAD).revisionStart()
                .blockedByOrNull());

        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        assertEquals(RevisionStartBlock.NOT_RETURNED_FOR_DOCUMENTS, h.surveys.block(own(SALE_A), LEAD).revisionStart()
                .blockedByOrNull(), "a DRAFT");
        h.submitted(checklist.getId(), SALE_A);
        h.decided(checklist.getId(), DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, LEADER);

        h.lead(LEAD, "ON_HOLD", SALE_A);
        assertEquals(RevisionStartBlock.LEAD_STATUS, h.surveys.block(own(SALE_A), LEAD).revisionStart()
                .blockedByOrNull());

        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        assertEquals(RevisionStartBlock.NOT_ASSIGNEE, h.surveys.block(all(LEADER), LEAD).revisionStart()
                .blockedByOrNull());

        h.directory.makeIneligible(SALE_A);
        assertEquals(RevisionStartBlock.ASSIGNEE_INELIGIBLE, h.surveys.block(own(SALE_A), LEAD).revisionStart()
                .blockedByOrNull());
    }

    @Test
    void block_revisionStart_visitWithoutARevision_isNotAMissingVisit() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);

        assertEquals(RevisionStartBlock.NOT_RETURNED_FOR_DOCUMENTS, h.surveys.block(own(SALE_A), LEAD).revisionStart()
                .blockedByOrNull());
    }

    @Test
    void block_revisionStart_resurveyReturn_notForTheSameVisit() {
        returned(NeedsChangesKind.RESURVEY_REQUIRED);

        assertEquals(RevisionStartBlock.NOT_RETURNED_FOR_DOCUMENTS, h.surveys.block(own(SALE_A), LEAD).revisionStart()
                .blockedByOrNull());
    }

    @Test
    void block_bookingBlockedBySubmittedAndPassed() {
        SurveyChecklist checklist = submitted();

        BookingState whileSubmitted = h.surveys.block(own(SALE_A), LEAD).booking();
        assertFalse(whileSubmitted.allowed());
        assertEquals(BookingBlock.CHECKLIST_SUBMITTED, whileSubmitted.blockedBy());

        h.evidence.saveAll(checklist.getId(), List.of());
        h.decided(checklist.getId(), DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "QUALIFIED", SALE_A);

        assertEquals(BookingBlock.CHECKLIST_PASSED, h.surveys.block(own(SALE_A), LEAD).booking().blockedBy());
    }

    @Test
    void block_summaryStatusOutcome() {
        SurveyChecklist checklist = submitted();

        ChecklistSummaryView whileSubmitted = h.surveys.block(own(SALE_A), LEAD).appointments().get(0).checklists()
                .get(0);
        assertEquals(ChecklistStatus.SUBMITTED, whileSubmitted.status());
        assertNull(whileSubmitted.outcomeOrNull());

        h.decided(checklist.getId(), DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, LEADER);

        ChecklistSummaryView decided = h.surveys.block(own(SALE_A), LEAD).appointments().get(0).checklists().get(0);
        assertEquals(ChecklistStatus.NEEDS_CHANGES, decided.status());
        assertEquals(DecisionOutcome.NEEDS_CHANGES, decided.outcomeOrNull());
    }

    @Test
    void block_resumeTargetQualified() {
        passed();
        h.lead(LEAD, "ON_HOLD", SALE_A);

        SurveyBlock block = h.surveys.block(own(SALE_A), LEAD);

        assertTrue(block.resumeAllowed());
        assertEquals(ResumeTarget.QUALIFIED, block.resumeTargetOrNull());
    }

    @Test
    void block_resumeTargetScheduled_withACompletableRecord() {
        submitted();
        h.lead(LEAD, "ON_HOLD", SALE_A);

        assertEquals(ResumeTarget.SURVEY_SCHEDULED, h.surveys.block(own(SALE_A), LEAD).resumeTargetOrNull());
    }

    /** The read answers as the command would (plan PD-7). */
    @Test
    void block_resumeNotAllowed_afterAReturnForAResurvey() {
        returned(NeedsChangesKind.RESURVEY_REQUIRED);
        h.lead(LEAD, "ON_HOLD", SALE_A);

        SurveyBlock block = h.surveys.block(own(SALE_A), LEAD);

        assertFalse(block.resumeAllowed());
        assertNull(block.resumeTargetOrNull());
    }

    @Test
    void block_surveyRecord_forTheTransitionMenu() {
        assertEquals(new SurveyRecordView(false, false, null), recordOfNewLead());

        SurveyChecklist checklist = submitted();
        assertEquals(new SurveyRecordView(false, true, null), h.surveys.block(own(SALE_A), LEAD).survey());

        h.evidence.saveAll(checklist.getId(), List.of());
        h.decided(checklist.getId(), DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "QUALIFIED", SALE_A);

        SurveyRecordView record = h.surveys.block(own(SALE_A), LEAD).survey();
        assertTrue(record.hasPassed());
        assertFalse(record.hasSubmitted());
        assertTrue(record.passedOrNull().usable(), "spec 4.7: also on the survey block");
    }

    private SurveyRecordView recordOfNewLead() {
        h.lead(OTHER_LEAD, "CONTACTED", SALE_A);
        return h.surveys.block(own(SALE_A), OTHER_LEAD).survey();
    }

    @Test
    void block_visitedAppointments_listTheirChecklistsWithEditabilityAndMissingCount() {
        h.started(LEAD, SALE_A);
        SurveyAppointment second = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        h.commands.start(own(SALE_A), new vn.sunvibe.survey.core.application.command.StartChecklistCommand(
                second.getId().getValue(), null));

        SurveyBlock block = h.surveys.block(own(SALE_A), LEAD);

        AppointmentView newer = block.appointments().get(0);
        AppointmentView older = block.appointments().get(1);
        ChecklistSummaryView current = newer.checklists().get(0);
        assertEquals(2, current.checklistNo());
        assertTrue(current.editability().editable());
        assertEquals(11, current.missingCount());
        assertEquals(SALE_A, current.holderUserId());
        assertEquals(ReadOnlyReason.NEWER_VISIT, older.checklists().get(0).editability().reason());
        assertEquals("Sale A", block.nameOf(current.holderUserId()));
    }

    /** survey-media spec 4.6: the summary's missingCount counts the evidence items, as the checklist read lists them. */
    @Test
    void block_photosAvailable_missingCountEqualsTheReadsMissingItems() {
        h.switches.uploads = true;
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        h.wholeWithSpaceOne(checklist.getId());
        h.readyPhoto(checklist.getId(), EvidenceCategory.KITCHEN, MediaPlacement.space(SPACE_1, "A1"));

        ChecklistSummaryView summary = h.surveys.block(own(SALE_A), LEAD).appointments().get(0).checklists().get(0);
        List<MissingItem> missing = h.queries.read(own(SALE_A), checklist.getId().getValue()).readiness().missing();

        assertEquals(missing.size(), summary.missingCount());
        assertEquals(3, missing.stream().filter(item -> item.group() == ChecklistGroup.EVIDENCE).count(),
                "exterior, main space and bathroom");
    }

    @Test
    void block_notVisitedAppointment_hasNoChecklists() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        h.scheduled(LEAD, SALE_A);

        assertEquals(List.of(), h.surveys.block(own(SALE_A), LEAD).appointments().get(0).checklists());
    }

    /** W16-21: a hidden existing lead writes one LEAD event; an unknown lead id none. */
    @Test
    void block_hiddenExistingLead_writesALeadEvent_unknownNone() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);

        assertThrows(SurveyLeadNotFoundException.class, () -> h.surveys.block(own(SALE_B), LEAD));
        assertThrows(SurveyLeadNotFoundException.class, () -> h.surveys.block(own(SALE_B), OTHER_LEAD));

        assertEquals(1, h.securityEvents.all().size());
        assertEquals(SecurityResourceType.LEAD, h.securityEvents.all().get(0).resourceType());
        assertEquals(LEAD, h.securityEvents.all().get(0).resourceId());
    }

    /** spec A-10: a block read writes SURVEY_BLOCK with the lead id. */
    @Test
    void block_accessLog_isSurveyBlockWithTheLeadId() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);

        h.surveys.block(own(SALE_A), LEAD);

        SurveyAccessLog log = h.accessLogs.all().get(0);
        assertEquals(AccessResourceType.SURVEY_BLOCK, log.resourceTypeOrNull());
        assertEquals(LEAD, log.resourceIdOrNull());
    }
}
