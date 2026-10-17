package vn.sunvibe.auth.core.domain.exception.auth;

import vn.sunvibe.shared.exception.BusinessRuleViolationException;

import java.io.Serial;

/**
 * A password-reset token cannot be used: unknown, expired, already used, or
 * burned after too many wrong codes. Answered with 400.
 */
public class PasswordResetTokenException extends BusinessRuleViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public PasswordResetTokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** Why the reset token was refused. */
    public enum Reason {
        /** The token is unknown, expired or already used, or its account is gone. */
        TOKEN_INVALID,
        /** The token was burned after too many wrong codes. */
        ATTEMPTS_EXCEEDED
    }
}
