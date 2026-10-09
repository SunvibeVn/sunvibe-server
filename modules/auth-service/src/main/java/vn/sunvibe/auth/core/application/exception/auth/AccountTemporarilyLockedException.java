package vn.sunvibe.auth.core.application.exception.auth;

import lombok.Getter;

import java.io.Serial;
import java.time.Duration;

/**
 * Password login refused because of too many wrong passwords. Answered with
 * 423 Locked and a {@code Retry-After} header.
 */
@Getter
public class AccountTemporarilyLockedException extends AuthenticationFailedException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** How long until the lock lifts. */
    private final Duration retryAfter;

    public AccountTemporarilyLockedException(Duration retryAfter) {
        super("Too many failed login attempts. Please try again in " + minutes(retryAfter) + " minute(s).");
        this.retryAfter = retryAfter;
    }

    private static long minutes(Duration duration) {
        // Round up so "try again in 0 minutes" is never shown
        return Math.max(1, (duration.toSeconds() + 59) / 60);
    }
}
