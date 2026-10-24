package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;

import java.util.List;
import java.util.Map;

/**
 * The Survey section of a lead (survey-schedule spec 7.2): the current intent cycle, whether and how a booking is
 * possible, the current visit, whether the survey work can be resumed, the appointments (newest first) and the
 * resumptions (newest first). {@code staffNames} maps every staff id of the block to its current full name (absent
 * when unknown), from one lookup. Reading it is access-logged (survey-schedule spec A-13). Private: never logged.
 */
public record SurveyBlock(String leadId, int intentCycle, BookingState booking, String currentVisitId,
                          boolean resumeAllowed, List<AppointmentView> appointments,
                          List<ResumptionView> resumptions, ChecklistStart checklistStart,
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
