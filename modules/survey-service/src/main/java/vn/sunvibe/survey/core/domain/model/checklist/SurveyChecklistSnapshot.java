package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * What the editability rule reads of a checklist (survey-checklist spec 4.4): its id, the VISITED appointment it
 * belongs to, its intent cycle, its holder and its status.
 */
public record SurveyChecklistSnapshot(String checklistId, String visitAppointmentId, int intentCycle,
                                      String holderUserId, ChecklistStatus status) {
}
