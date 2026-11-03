package vn.sunvibe.survey.core.domain.model.media;

/** The state of a media job (survey-media spec 5.2). */
public enum MediaJobState {
    /** Due at its due time; claimable. */
    PENDING,
    /** Finished, with its last error code when it gave up. */
    DONE,
    /** Ended without work (the photo was removed or expired). */
    CANCELLED
}
