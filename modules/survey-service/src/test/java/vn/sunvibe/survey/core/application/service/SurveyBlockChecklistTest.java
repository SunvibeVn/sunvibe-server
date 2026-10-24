package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.query.AppointmentView;
import vn.sunvibe.survey.core.application.query.ChecklistSummaryView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadOnlyReason;
import vn.sunvibe.survey.core.domain.policy.checklist.StartBlock;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.OTHER_LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;

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
        assertEquals(StartBlock.ALREADY_STARTED, h.surveys.block(own(SALE_A), LEAD).checklistStart()
                .blockedByOrNull());
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
