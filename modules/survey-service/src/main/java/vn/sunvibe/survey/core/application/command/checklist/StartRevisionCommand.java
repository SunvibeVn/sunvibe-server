package vn.sunvibe.survey.core.application.command.checklist;

/**
 * A new revision on the same visit as sent (survey-checklist submission spec 4.6, 7.1, A-7): the revision returned for
 * documents and the intake declaration, null when the caller held that revision.
 */
public record StartRevisionCommand(String checklistId, IntakeInput intakeOrNull) {
}
