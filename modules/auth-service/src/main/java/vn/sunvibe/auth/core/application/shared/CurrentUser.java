package vn.sunvibe.auth.core.application.shared;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The user the current request is authenticated as (set by the JWT request filter).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The username (phone number) of the signed-in user.
     *
     * @throws InsufficientAuthenticationException if the request is not authenticated
     */
    public static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException("User not authenticated");
        }
        return authentication.getName();
    }
}
