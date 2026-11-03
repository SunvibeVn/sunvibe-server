package vn.sunvibe.survey.core.domain.model.media;

/** The system-driven status of a survey photo (survey-media spec 4.2). */
public enum MediaStatus {
    /** Created; no content upload holds the claim yet. */
    AWAITING_CONTENT,
    /** A content upload holds the claim and is streaming the bytes. */
    RECEIVING,
    /** The bytes were committed (survey-media BR-17); the worker checks and normalizes them. */
    PROCESSING,
    /** Final: the three variants exist; servable while not removed, not held and its content present. */
    READY,
    /** Final: the file was not accepted, with a content reason. */
    REJECTED,
    /** Final: processing could not complete (PROCESSING_UNAVAILABLE); the Sale uploads the file again. */
    FAILED,
    /** Final: removed while PROCESSING; never READY (survey-media BR-17). */
    CANCELLED,
    /** Final: no content was received within 60 minutes, or a takeover ended the upload. */
    UPLOAD_EXPIRED;

    /** READY, REJECTED, FAILED, CANCELLED and UPLOAD_EXPIRED never change status again. */
    public boolean isFinal() {
        return this == READY || this == REJECTED || this == FAILED || this == CANCELLED || this == UPLOAD_EXPIRED;
    }

    /** The statuses that hold received content: PROCESSING and every outcome of processing. */
    public boolean hasReceivedContent() {
        return this == PROCESSING || this == READY || this == REJECTED || this == FAILED || this == CANCELLED;
    }
}
