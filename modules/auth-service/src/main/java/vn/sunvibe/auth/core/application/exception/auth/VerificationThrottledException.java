package vn.sunvibe.auth.core.application.exception.auth;

import lombok.Getter;
import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;
import java.time.Duration;

/**
 * A new code was asked for too soon or too often. Answered with 429 and a
 * {@code Retry-After} header.
 */
@Getter
public class VerificationThrottledException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    private final Duration retryAfter;

    public VerificationThrottledException(Reason reason, String message, Duration retryAfter) {
        super(message);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    /** Which throttle refused the new code. */
    public enum Reason {
        /** Inside the resend cooldown after the previous code. */
        RESEND_TOO_SOON,
        /** The daily number of codes was already sent. */
        DAILY_LIMIT
    }
}
