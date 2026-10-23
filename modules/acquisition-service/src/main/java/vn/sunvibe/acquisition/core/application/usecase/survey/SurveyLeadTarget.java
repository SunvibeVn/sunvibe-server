package vn.sunvibe.acquisition.core.application.usecase.survey;

/** The lead status a survey command asks for (survey-schedule spec 4.3). */
public enum SurveyLeadTarget {
    /** The lead keeps its status (its version still goes up). */
    KEEP,
    /** The lead moves to SURVEY_SCHEDULED: a first booking, a re-survey from CONTACTED or a resumption. */
    SURVEY_SCHEDULED,
    /** The lead goes back to CONTACTED: a cancellation without a current visit. */
    CONTACTED
}
