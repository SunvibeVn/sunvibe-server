package vn.sunvibe.survey.core.domain.model.checklist;

/** A user named by a stored row of a revision (survey-checklist submission plan PD-2). */
public record ChecklistPerson(String checklistId, String userId) {
}
