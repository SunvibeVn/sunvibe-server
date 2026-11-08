package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.util.Optional;

/**
 * Whether a checklist can be started now (survey-checklist US-1 AC-1, BR-1, BR-12, BR-14, spec 4.3, 7.2): on the
 * current visit when its intent is long-term rental, the lead is SURVEY_SCHEDULED and the visit has no revision, by the
 * lead's eligible assignee; an assignee who did not perform the visit declares where the information comes from.
 */
public final class ChecklistStartPolicy {

    private ChecklistStartPolicy() {
    }

    public static ChecklistStart of(Optional<SurveyAppointment> currentVisit, boolean currentVisitHasRevision,
                                    LeadStatus leadStatus, String assigneeUserId, boolean assigneeEligible,
                                    String viewerUserId) {
        if (currentVisit.isEmpty()) {
            return ChecklistStart.blocked(StartBlock.NO_CURRENT_VISIT);
        }
        SurveyAppointment visit = currentVisit.get();
        if (visit.getIntent() != SurveyIntent.LONG_TERM_RENT) {
            return ChecklistStart.blocked(StartBlock.INTENT);
        }
        if (leadStatus != LeadStatus.SURVEY_SCHEDULED) {
            return ChecklistStart.blocked(StartBlock.LEAD_STATUS);
        }
        if (currentVisitHasRevision) {
            return ChecklistStart.blocked(StartBlock.CHECKLIST_EXISTS);
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
