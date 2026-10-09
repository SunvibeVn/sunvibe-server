package vn.sunvibe.auth.core.domain.exception.auth;

import vn.sunvibe.common.exception.BusinessRuleViolationException;

import java.io.Serial;

public class PasswordResetTokenException extends BusinessRuleViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PasswordResetTokenException(String message) {
        super(message);
    }
}
