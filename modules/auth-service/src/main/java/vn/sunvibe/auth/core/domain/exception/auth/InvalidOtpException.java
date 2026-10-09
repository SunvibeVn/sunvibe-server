package vn.sunvibe.auth.core.domain.exception.auth;

import vn.sunvibe.common.exception.DomainException;

import java.io.Serial;

/**
 * A wrong OTP code was entered for a still-usable password-reset token.
 * Answered with 400.
 */
public class InvalidOtpException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidOtpException(String message) {
        super(message);
    }
}
