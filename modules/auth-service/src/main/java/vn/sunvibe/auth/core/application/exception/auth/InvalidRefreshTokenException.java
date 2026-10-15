package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * The refresh token cannot be exchanged (missing, unknown, expired, revoked,
 * reused, or its owner may no longer sign in). Answered with 401 and cleared
 * cookies: the client must sign in again.
 */
public class InvalidRefreshTokenException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
