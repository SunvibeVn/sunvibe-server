package vn.sunvibe.survey.core.application.command;

/** The holder's submission of a revision as sent (survey-checklist submission spec 4.3, 7.1, A-6). */
public record SubmitChecklistCommand(String checklistId, Long expectedVersion) {
}
