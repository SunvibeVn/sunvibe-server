package vn.sunvibe.auth.core.application.shared;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.shared.security.AuthenticatedUser;

/**
 * The user the current request is authenticated as (set by the JWT request filter).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The id of the signed-in user: the only identity of a request, whatever identifiers the account has
     * (auth-email-access BR-24).
     *
     * @throws InsufficientAuthenticationException if the request is not authenticated
     */
    public static String userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new InsufficientAuthenticationException("User not authenticated");
        }
        return user.getUserId();
    }
}
