package vn.sunvibe.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The one reader of the signed-in principal for the viewer and actor resolvers of every module (clean-up spec S-8),
 * beside {@link AuthenticatedUser}, its contract (ADR-009). A request that reaches a resolver has passed the JWT filter
 * and an authorization rule, so a missing or foreign principal is a server fault: it fails closed with an
 * {@link IllegalStateException} (a 500), never with a guessed caller. auth-service's {@code CurrentUser} answers 401
 * instead and refuses an anonymous token, a different contract that stays there.
 */
public final class CurrentPrincipal {

    private CurrentPrincipal() {
    }

    /** The caller's user id: an authenticated {@link AuthenticatedUser} with a non-blank id, or a fail-closed error. */
    public static String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId();
        }
        throw new IllegalStateException("Principal does not expose a user id.");
    }

    /**
     * The authorities the caller reaches through {@code roleHierarchy}, so SUPER_ADMIN reaches ADMIN; empty when the
     * request has no authentication.
     */
    public static Set<String> expandedAuthorities(RoleHierarchy roleHierarchy) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> own =
                authentication == null ? List.of() : authentication.getAuthorities();
        return roleHierarchy.getReachableGrantedAuthorities(own).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /** Whether the caller holds {@code authority} itself, without the role hierarchy; false without authentication. */
    public static boolean hasOwnAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
