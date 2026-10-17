package vn.sunvibe.shared.constant.error;

/** Stable machine-readable error codes of the error body (ADR-010). The value of each constant is its own name. */
public final class ErrorCode {

    /** One or more request fields failed validation. */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** An unparsable body or a request the endpoint cannot process (for example an unsupported media type). */
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    /** The requested resource does not exist or is not visible to the caller. */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** The same idempotency key was sent again with a different request body. */
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

    /** The request body is larger than the allowed size. */
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";

    /** The caller sent too many requests in a short time and must retry later. */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    /** The requested action is not allowed from the current status of the resource. */
    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    /** Someone else changed the resource first, so the caller must reload it and try again. */
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    /** The chosen assignee cannot take this work (not a staff member, inactive or without the needed role). */
    public static final String INELIGIBLE_ASSIGNEE = "INELIGIBLE_ASSIGNEE";

    /** An unexpected server error; the details are only in the server log, found by the correlation id. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCode() {}
}
