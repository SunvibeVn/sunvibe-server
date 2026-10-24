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

    /** The lead already has a scheduled survey appointment (survey-schedule US-1 AC-4). */
    public static final String SURVEY_ALREADY_SCHEDULED = "SURVEY_ALREADY_SCHEDULED";

    /** A survey cannot be booked for the lead now: its status or its intent does not allow it. */
    public static final String SURVEY_NOT_ALLOWED = "SURVEY_NOT_ALLOWED";

    /** A held lead has no visit its survey work could resume from. */
    public static final String NO_CURRENT_VISIT = "NO_CURRENT_VISIT";

    /** The surveyor (the lead's assignee) can no longer take leads, so the survey command is refused. */
    public static final String SURVEYOR_INELIGIBLE = "SURVEYOR_INELIGIBLE";

    /** The person named as having reached the property did not hold the appointment at that time. */
    public static final String PERFORMER_MISMATCH = "PERFORMER_MISMATCH";

    /** A survey checklist cannot be started or taken over now: the visit, the intent, the lead status or the holder. */
    public static final String CHECKLIST_NOT_ALLOWED = "CHECKLIST_NOT_ALLOWED";

    /** The visit already has a survey checklist. */
    public static final String CHECKLIST_ALREADY_STARTED = "CHECKLIST_ALREADY_STARTED";

    /** The survey checklist can no longer be changed; the reason is on its next read. */
    public static final String CHECKLIST_READ_ONLY = "CHECKLIST_READ_ONLY";

    /** The caller is the new assignee and must take the survey checklist over before saving. */
    public static final String CHECKLIST_TAKEOVER_REQUIRED = "CHECKLIST_TAKEOVER_REQUIRED";

    /** An unexpected server error; the details are only in the server log, found by the correlation id. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** Sign-in refused: unknown phone number, wrong password, or an account that may not sign in. */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    /** Sign-in is temporarily blocked after too many wrong passwords; retry after the given time. */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    /** The account is locked or terminated, so the requested action is refused. */
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    /** The browser session cannot be refreshed (missing, unknown, expired, revoked or reused refresh token). */
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";

    /** The request has no Origin header or comes from an origin that may not use the session endpoints. */
    public static final String ORIGIN_NOT_ALLOWED = "ORIGIN_NOT_ALLOWED";

    /** The verification code is wrong; the caller may try again. */
    public static final String CODE_INCORRECT = "CODE_INCORRECT";

    /** There is no usable verification code (none sent, expired or replaced); a new one must be requested. */
    public static final String CODE_EXPIRED = "CODE_EXPIRED";

    /** The verification code was burned after too many wrong tries; a new one must be requested. */
    public static final String CODE_ATTEMPTS_EXCEEDED = "CODE_ATTEMPTS_EXCEEDED";

    /** The contact (phone or e-mail) is already verified. */
    public static final String ALREADY_VERIFIED = "ALREADY_VERIFIED";

    /** E-mail verification was asked for an account that has no e-mail. */
    public static final String NO_EMAIL = "NO_EMAIL";

    /** The registration token is missing, used, expired or issued for another phone number. */
    public static final String REGISTRATION_TOKEN_INVALID = "REGISTRATION_TOKEN_INVALID";

    /** The password-reset token is unknown, expired or already used. */
    public static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";

    /** A new code was asked for inside the resend cooldown; retry after the given time. */
    public static final String CODE_RESEND_TOO_SOON = "CODE_RESEND_TOO_SOON";

    /** The daily number of codes was already sent; retry after the given time. */
    public static final String CODE_DAILY_LIMIT = "CODE_DAILY_LIMIT";

    private ErrorCode() {}
}
