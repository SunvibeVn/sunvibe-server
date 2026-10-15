package vn.sunvibe.auth.core.application.exception.role;

import java.io.Serial;

/**
 * A role cannot be granted to an account that may not sign in (locked or terminated; auth-roles BR-10, E-7).
 * Answered with 422 on field {@code userId}.
 */
public class InactiveAccountException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InactiveAccountException() {
        super("The account is not active.");
    }
}
