package vn.sunvibe.survey.core.application.query.appointment;

import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;
import vn.sunvibe.survey.core.domain.policy.checklist.ResumeTarget;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStart;

import java.util.List;
import java.util.Map;

/**
 * The Survey section of a lead (survey-schedule spec 7.2): the current intent cycle, whether and how a booking is
 * possible, the current visit, whether the survey work can be resumed and where to, the appointments (newest first),
 * the resumptions (newest first), the viewer's checklist and revision starts and the cycle's survey record.
 * {@code staffNames} maps every staff id of the block to its current full name (absent when unknown), from one lookup.
 * Reading it is access-logged (survey-schedule spec A-13). Private: never logged.
 */
public record SurveyBlock(String leadId, int intentCycle, BookingState booking, String currentVisitId,
                          boolean resumeAllowed, List<AppointmentView> appointments,
                          List<ResumptionView> resumptions, ChecklistStart checklistStart,
                          RevisionStart revisionStart, ResumeTarget resumeTargetOrNull, SurveyRecordView survey,
                          Map<String, String> staffNames) {

    public SurveyBlock {
        appointments = List.copyOf(appointments);
        resumptions = List.copyOf(resumptions);
        staffNames = Map.copyOf(staffNames);
    }

    /** The name of a staff id, or null when the id is null or has no name. */
    public String nameOf(String userId) {
        return userId == null ? null : staffNames.get(userId);
    }

    @Override
    public String toString() {
        return "SurveyBlock[" + leadId + "]";
    }
}
