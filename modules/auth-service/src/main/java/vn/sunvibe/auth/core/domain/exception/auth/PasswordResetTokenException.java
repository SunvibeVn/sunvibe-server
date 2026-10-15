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

    public PasswordResetTokenException(String message) {
        super(message);
    }
}
