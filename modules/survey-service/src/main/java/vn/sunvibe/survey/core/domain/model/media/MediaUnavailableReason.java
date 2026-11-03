package vn.sunvibe.survey.core.domain.model.media;

/** Why a photo's content cannot be read now (survey-media spec 7.2), the first that applies in this order. */
public enum MediaUnavailableReason {
    /** The photo was removed (BR-8). */
    REMOVED,
    /** An ADMIN holds the photo (spec A-23). */
    HELD,
    /** The photo is not READY: still uploading or processing, or never accepted. */
    NOT_READY,
    /** Its content was deleted after the removal (spec 4.7). */
    CONTENT_DELETED,
    /** Its content is missing after a database restore (spec A-17). */
    CONTENT_MISSING
}
