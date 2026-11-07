package vn.sunvibe.survey.core.application.usecase.common;

/**
 * The survey record of one intent cycle as the lead rules ask for it (survey-checklist submission spec 4.8): a PASSED
 * revision and whether it is usable, a SUBMITTED revision, and a record that can still be completed.
 */
public record LeadSurveyState(boolean hasPassed, boolean passedUsable, boolean hasSubmitted, boolean completable) {
}
