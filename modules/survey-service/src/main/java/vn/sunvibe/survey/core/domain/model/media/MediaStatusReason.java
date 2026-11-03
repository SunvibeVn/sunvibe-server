package vn.sunvibe.survey.core.domain.model.media;

/** Why a photo ended REJECTED, FAILED, CANCELLED or UPLOAD_EXPIRED (survey-media spec 4.2, 4.5, 5.2). */
public enum MediaStatusReason {
    /** REJECTED: not a supported still image format (survey-media spec 4.5). */
    UNSUPPORTED_FORMAT,
    /** REJECTED: not readable, truncated or corrupt, or over the processor limits twice. */
    UNREADABLE,
    /** REJECTED: an animation or image sequence. */
    ANIMATED,
    /** REJECTED: a still with a video track or appended video (a motion photo). */
    MOTION_PHOTO,
    /** REJECTED: another payload inside or after the image. */
    COMPOSITE_FILE,
    /** REJECTED: more than 120,000,000 pixels or 16,384 px on a side (survey-media spec A-3). */
    TOO_MANY_PIXELS,
    /** REJECTED: the malware scan found something (survey-media spec 4.5). */
    MALWARE_DETECTED,
    /** REJECTED: the scanner answered with a limit or error for this file three times (spec A-7, A-27). */
    SCAN_LIMIT_EXCEEDED,
    /** FAILED: processing could not complete in time or attempts (survey-media spec A-7). */
    PROCESSING_UNAVAILABLE,
    /** CANCELLED: removed while PROCESSING. */
    REMOVED,
    /** UPLOAD_EXPIRED: no content arrived in time. */
    CONTENT_NOT_RECEIVED;

    /** The eight content reasons of a REJECTED photo. */
    public boolean isRejection() {
        return this != PROCESSING_UNAVAILABLE && this != REMOVED && this != CONTENT_NOT_RECEIVED;
    }
}
