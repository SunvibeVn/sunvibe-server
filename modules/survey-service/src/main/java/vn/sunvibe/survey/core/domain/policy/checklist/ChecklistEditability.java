package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistSnapshot;

import java.util.Optional;

/**
 * Who may edit a draft (survey-checklist spec 4.4, BR-12, BR-14): a DRAFT on the lead's current visit of its own intent
 * cycle, the lead in SURVEY_SCHEDULED, the viewer the lead's assignee and the holder, the assignee eligible. The
 * reasons are checked in this order; a SCHEDULED re-survey does not lock the draft, because the VISITED appointment
 * stays the current visit (spec 4.3).
 */
public final class ChecklistEditability {

    private static final String SURVEY_SCHEDULED = "SURVEY_SCHEDULED";

    private ChecklistEditability() {
    }

    /**
     * @param currentVisitId the lead's current visit (latest VISITED of the lead's current cycle), empty when none
     */
    public static Editability of(SurveyChecklistSnapshot checklist, String leadStatus, int leadIntentCycle,
                                 String assigneeUserId, boolean assigneeEligible, Optional<String> currentVisitId,
                                 String viewerUserId) {
        if (checklist.intentCycle() != leadIntentCycle) {
            return Editability.readOnly(ReadOnlyReason.INTENT_CHANGED);
        }
        if (checklist.status() != ChecklistStatus.DRAFT
                || !currentVisitId.equals(Optional.of(checklist.visitAppointmentId()))) {
            return Editability.readOnly(ReadOnlyReason.NEWER_VISIT);
        }
        if (!SURVEY_SCHEDULED.equals(leadStatus)) {
            return Editability.readOnly(ReadOnlyReason.LEAD_STATUS);
        }
        if (!viewerUserId.equals(assigneeUserId)) {
            return Editability.readOnly(ReadOnlyReason.NOT_ASSIGNEE);
        }
        if (!assigneeEligible) {
            return Editability.readOnly(ReadOnlyReason.ASSIGNEE_INELIGIBLE);
        }
        if (!viewerUserId.equals(checklist.holderUserId())) {
            return Editability.readOnly(ReadOnlyReason.TAKEOVER_REQUIRED);
        }
        return Editability.EDITABLE;
    }
}
