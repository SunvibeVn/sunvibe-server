package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.common.exception.ApplicationException;

import java.io.Serial;

/**
 * A verification code cannot be sent or confirmed (wrong/expired code, nothing
 * to verify, already verified). Answered with 400.
 */
public class VerificationException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public VerificationException(String message) {
        super(message);
    }
}
