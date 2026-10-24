package vn.sunvibe.survey.core.application.command;

/** Start a checklist on a visit (survey-checklist spec 7.1); the intake is null when the performer starts. */
public record StartChecklistCommand(String appointmentId, IntakeInput intakeOrNull) {
}
