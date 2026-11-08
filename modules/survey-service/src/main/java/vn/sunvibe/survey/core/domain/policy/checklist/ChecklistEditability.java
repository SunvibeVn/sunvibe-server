package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistEditState;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.util.Optional;

/**
 * Who may edit a draft (survey-checklist spec 4.4, BR-12, BR-14): a DRAFT on the lead's current visit of its own intent
 * cycle, the lead in SURVEY_SCHEDULED, the viewer the lead's assignee and the holder, the assignee eligible. The
 * reasons are checked in this order; a SCHEDULED re-survey does not lock the draft, because the VISITED appointment
 * stays the current visit (spec 4.3).
 */
public final class ChecklistEditability {

    private ChecklistEditability() {
    }

    /**
     * @param currentVisitId the lead's current visit (latest VISITED of the lead's current cycle), empty when none
     */
    public static Editability of(ChecklistEditState checklist, LeadStatus leadStatus, int leadIntentCycle,
                                 String assigneeUserId, boolean assigneeEligible, Optional<String> currentVisitId,
                                 String viewerUserId) {
        if (checklist.intentCycle() != leadIntentCycle) {
            return Editability.readOnly(ReadOnlyReason.INTENT_CHANGED);
        }
        if (checklist.status() == ChecklistStatus.SUBMITTED) {
            return Editability.readOnly(ReadOnlyReason.SUBMITTED);
        }
        if (checklist.status().isDecided()) {
            return Editability.readOnly(ReadOnlyReason.DECIDED);
        }
        if (!currentVisitId.equals(Optional.of(checklist.visitAppointmentId()))) {
            return Editability.readOnly(ReadOnlyReason.NEWER_VISIT);
        }
        if (leadStatus != LeadStatus.SURVEY_SCHEDULED) {
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
