package vn.sunvibe.auth.core.infrastructure.security.userdetails;

import org.springframework.security.core.AuthenticatedPrincipal;
import vn.sunvibe.shared.security.AuthenticatedUser;

/**
 * Principal set by the JWT request filter: only the user id, no identifier, password hash or e-mail.
 * {@link #getName()} is the user id too, so {@code Authentication.getName()} is the identity of the request.
 */
public record AuthenticatedUserPrincipal(String userId) implements AuthenticatedUser, AuthenticatedPrincipal {

    public AuthenticatedUserPrincipal {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return userId;
    }
}
