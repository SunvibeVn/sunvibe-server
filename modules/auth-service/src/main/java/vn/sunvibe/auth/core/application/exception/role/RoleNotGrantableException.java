package vn.sunvibe.auth.core.application.exception.role;

import java.io.Serial;

/**
 * The role is not one the role admin API grants or revokes: SUPER_ADMIN, an old role or an unknown name
 * (auth-roles E-6). Answered with 400 on field {@code role}.
 */
public class RoleNotGrantableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleNotGrantableException() {
        super("This role cannot be granted or revoked.");
    }
}
