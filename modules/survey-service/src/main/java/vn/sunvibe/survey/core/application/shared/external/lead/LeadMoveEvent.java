package vn.sunvibe.survey.core.application.shared.external.lead;

/** The survey commands that move or touch a lead; each becomes the lead History entry of the same name. */
public enum LeadMoveEvent {
    /** A survey or re-survey was booked. */
    SURVEY_BOOKED,
    /** The scheduled appointment changed. */
    SURVEY_CHANGED,
    /** The owner's confirmation was recorded. */
    SURVEY_CONFIRMED,
    /** The owner's confirmation was removed. */
    SURVEY_UNCONFIRMED,
    /** The scheduled appointment was cancelled by staff. */
    SURVEY_CANCELLED,
    /** A visit was recorded. */
    SURVEY_VISITED,
    /** The survey work of a held lead was resumed. */
    SURVEY_RESUMED
}
