package vn.sunvibe.survey.core.application.shared.external.lead;

/**
 * A lead the viewer can see with its current intent cycle, status (enum name), assignee and request code (survey-checklist
 * submission plan PD-4).
 */
public record LeadCycle(String leadId, int intentCycle, String status, String assignedUserIdOrNull,
                        String requestCode) {
}
