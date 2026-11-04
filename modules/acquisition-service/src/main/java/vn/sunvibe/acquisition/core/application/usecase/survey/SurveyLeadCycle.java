package vn.sunvibe.acquisition.core.application.usecase.survey;

/**
 * A visible lead with its current intent cycle, status (enum name), assignee and request code (survey-checklist
 * submission plan PD-4).
 */
public record SurveyLeadCycle(String leadId, int intentCycle, String status, String assignedUserIdOrNull,
                              String requestCode) {
}
