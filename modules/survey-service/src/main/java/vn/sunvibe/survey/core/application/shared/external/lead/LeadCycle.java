package vn.sunvibe.survey.core.application.shared.external.lead;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

/**
 * A lead the viewer can see with its current intent cycle, status (enum name), assignee and request code (survey-checklist
 * submission plan PD-4).
 */
public record LeadCycle(String leadId, int intentCycle, String statusName, String assignedUserIdOrNull,
                        String requestCode) {

    /** The status as an enum; a name it does not know fails here, loudly. */
    public LeadStatus status() {
        return LeadStatus.valueOf(statusName);
    }
}
