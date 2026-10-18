package vn.sunvibe.auth.core.application.exception.auth;

import java.io.Serial;

/**
 * The account is locked or terminated, so the action (a password reset) is
 * refused. A kind of {@link AuthenticationFailedException}; the v1 auth handler answers it with its own
 * ACCOUNT_DISABLED 401.
 */
public class AccountDisabledException extends AuthenticationFailedException {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String MESSAGE = "Account is locked or disabled.";

    public AccountDisabledException() {
        super(MESSAGE);
    }
}
