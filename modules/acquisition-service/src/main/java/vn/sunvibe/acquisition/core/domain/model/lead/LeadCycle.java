package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * A lead's current intent cycle with what a review queue row shows and checks: its status, assignee and request code
 * (survey-checklist submission plan PD-4).
 */
public record LeadCycle(String leadId, int intentCycle, OwnerLeadStatus status, String assignedUserIdOrNull,
                        String requestCode) {
}
