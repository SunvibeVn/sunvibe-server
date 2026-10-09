package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.common.exception.ApplicationException;

import java.io.Serial;

/**
 * Sign-in or credential check refused: wrong credentials, or an account that
 * may not sign in. Answered with 401.
 */
public class AuthenticationFailedException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
