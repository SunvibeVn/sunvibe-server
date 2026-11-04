package vn.sunvibe.survey.core.application.query;

/**
 * The survey record of the lead's current cycle on the survey block (survey-checklist submission spec 4.7, 7.2): what the
 * status menu needs and, with a PASSED revision, whether it can still be used.
 */
public record SurveyRecordView(boolean hasPassed, boolean hasSubmitted, PassedView passedOrNull) {
}
