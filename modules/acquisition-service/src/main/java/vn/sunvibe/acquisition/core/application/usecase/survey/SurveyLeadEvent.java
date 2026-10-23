package vn.sunvibe.acquisition.core.application.usecase.survey;

/**
 * The survey commands that move or touch a lead; each becomes the lead History entry of the same name
 * (survey-schedule spec 4.6, A-12).
 */
public enum SurveyLeadEvent {
    /** A survey or re-survey was booked (survey-schedule US-1, US-3 AC-2). */
    SURVEY_BOOKED,
    /** The scheduled appointment was changed (survey-schedule US-2 AC-1). */
    SURVEY_CHANGED,
    /** The owner's confirmation was recorded (survey-schedule US-1 AC-5). */
    SURVEY_CONFIRMED,
    /** The owner's confirmation was removed (survey-schedule US-1 AC-5). */
    SURVEY_UNCONFIRMED,
    /** The scheduled appointment was cancelled by staff (survey-schedule US-2 AC-2). */
    SURVEY_CANCELLED,
    /** A visit was recorded (survey-schedule US-3 AC-1). */
    SURVEY_VISITED,
    /** The survey work of a held lead was resumed (survey-schedule BR-11). */
    SURVEY_RESUMED
}
