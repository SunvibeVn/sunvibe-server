package vn.sunvibe.auth.core.infrastructure.security.userdetails;

import org.springframework.security.core.AuthenticatedPrincipal;
import vn.sunvibe.common.security.AuthenticatedUser;

/**
 * Principal set by the JWT request filter: only the user id and the username, no password hash or e-mail.
 * {@link #getName()} is the username, so {@code Authentication.getName()} is unchanged.
 */
public record AuthenticatedUserPrincipal(String userId, String username)
        implements AuthenticatedUser, AuthenticatedPrincipal {

    public AuthenticatedUserPrincipal {
        if (userId == null || userId.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("userId and username are required");
        }
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return username;
    }

    /** Without the username: it is the phone number (PII). */
    @Override
    public String toString() {
        return "AuthenticatedUserPrincipal[userId=" + userId + "]";
    }
}
