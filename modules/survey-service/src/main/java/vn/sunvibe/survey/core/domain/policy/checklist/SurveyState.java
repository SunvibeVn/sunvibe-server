package vn.sunvibe.survey.core.domain.policy.checklist;

/**
 * What the lead rules need of the survey record of one intent cycle (survey-checklist submission spec 4.8): a PASSED
 * revision and whether it is usable, a SUBMITTED revision, and a completable record.
 */
public record SurveyState(boolean hasPassed, boolean passedUsable, boolean hasSubmitted, boolean completable) {
}
