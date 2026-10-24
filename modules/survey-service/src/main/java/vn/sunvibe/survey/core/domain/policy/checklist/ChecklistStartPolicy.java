package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;

import java.util.Optional;

/**
 * Whether a checklist can be started now (survey-checklist US-1 AC-1, BR-1, BR-12, BR-14, spec 4.3, 7.2): on the
 * current visit when its intent is long-term rental, the lead is SURVEY_SCHEDULED and the visit has no draft, by the
 * lead's eligible assignee; an assignee who did not perform the visit declares where the information comes from.
 */
public final class ChecklistStartPolicy {

    private static final String SURVEY_SCHEDULED = "SURVEY_SCHEDULED";

    private ChecklistStartPolicy() {
    }

    public static ChecklistStart of(Optional<SurveyAppointment> currentVisit, boolean currentVisitHasDraft,
                                    String leadStatus, String assigneeUserId, boolean assigneeEligible,
                                    String viewerUserId) {
        if (currentVisit.isEmpty()) {
            return ChecklistStart.blocked(StartBlock.NO_CURRENT_VISIT);
        }
        SurveyAppointment visit = currentVisit.get();
        if (visit.getIntent() != SurveyIntent.LONG_TERM_RENT) {
            return ChecklistStart.blocked(StartBlock.INTENT);
        }
        if (!SURVEY_SCHEDULED.equals(leadStatus)) {
            return ChecklistStart.blocked(StartBlock.LEAD_STATUS);
        }
        if (currentVisitHasDraft) {
            return ChecklistStart.blocked(StartBlock.ALREADY_STARTED);
        }
        if (!viewerUserId.equals(assigneeUserId)) {
            return ChecklistStart.blocked(StartBlock.NOT_ASSIGNEE);
        }
        if (!assigneeEligible) {
            return ChecklistStart.blocked(StartBlock.ASSIGNEE_INELIGIBLE);
        }
        return new ChecklistStart(true, visit.getId().getValue(),
                !viewerUserId.equals(visit.getVisit().performedBy()), null);
    }
}
