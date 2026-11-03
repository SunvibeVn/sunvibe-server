package vn.sunvibe.survey.core.domain.model.media;

/** How a health probe ended (survey-media spec A-18). */
public enum ProbeResult {
    /** Ran and answered as expected. */
    PASSED,
    /** Ran and answered wrongly: pauses intake at once. */
    WRONG_ANSWER,
    /** Could not complete (no connection, no answer in time): pauses only on the second in a row. */
    INCOMPLETE
}
