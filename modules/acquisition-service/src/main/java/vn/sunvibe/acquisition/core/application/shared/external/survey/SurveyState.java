package vn.sunvibe.acquisition.core.application.shared.external.survey;

/**
 * What the lead rules ask of the survey record of one intent cycle (survey-checklist submission spec 4.8): a PASSED
 * revision and whether it is usable, a SUBMITTED revision, and a record that can still be completed.
 */
public record SurveyState(boolean hasPassed, boolean passedUsable, boolean hasSubmitted, boolean completable) {

    /** No revision that matters: a lead without a checklist. */
    public static final SurveyState NONE = new SurveyState(false, false, false, false);
}
