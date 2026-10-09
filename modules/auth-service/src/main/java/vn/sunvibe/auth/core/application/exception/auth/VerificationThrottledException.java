package vn.sunvibe.auth.core.application.exception.auth;

import lombok.Getter;
import vn.sunvibe.common.exception.ApplicationException;

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

    private final Duration retryAfter;

    public VerificationThrottledException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
