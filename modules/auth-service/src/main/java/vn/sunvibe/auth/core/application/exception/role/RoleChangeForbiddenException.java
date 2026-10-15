package vn.sunvibe.auth.core.application.exception.role;

import org.springframework.security.access.AccessDeniedException;

import java.io.Serial;

/**
 * The caller may not make this role change: an ADMIN changing ADMIN, or anyone changing their own roles
 * (auth-roles E-4, E-5, Q-5). An {@link AccessDeniedException}, so it gets the usual 403 body.
 */
public class RoleChangeForbiddenException extends AccessDeniedException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleChangeForbiddenException(String message) {
        super(message);
    }
}
