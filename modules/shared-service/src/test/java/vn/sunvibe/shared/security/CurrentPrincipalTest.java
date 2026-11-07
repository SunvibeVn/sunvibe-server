package vn.sunvibe.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The one reader of the signed-in principal for the viewer and actor resolvers (clean-up spec S-8). Anything but an
 * authenticated principal with a user id fails closed with an {@link IllegalStateException}, which the handlers answer
 * with a 500, never with a guessed caller.
 */
class CurrentPrincipalTest {

    private static final String USER_ID = "01J0000000000000000000USER";

    /** The same hierarchy as SecurityConfig.roleHierarchy() in app: SUPER_ADMIN implies ADMIN. */
    private static final RoleHierarchy HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireUserId_noAuthentication_failsClosed() {
        SecurityContextHolder.clearContext();

        assertFailsClosed();
    }

    @Test
    void requireUserId_notAuthenticated_failsClosed() {
        signIn(UsernamePasswordAuthenticationToken.unauthenticated(new Principal(USER_ID), null));

        assertFailsClosed();
    }

    @Test
    void requireUserId_otherPrincipal_failsClosed() {
        signIn(UsernamePasswordAuthenticationToken.authenticated("0909123456", null, roles("ROLE_ADMIN")));

        assertFailsClosed();
    }

    /** An anonymous token counts as authenticated in Spring Security; its principal is a plain string. */
    @Test
    void requireUserId_anonymous_failsClosed() {
        signIn(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertFailsClosed();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void requireUserId_blankId_failsClosed(String userId) {
        signIn(staff(userId, "ROLE_ADMIN"));

        assertFailsClosed();
    }

    @Test
    void requireUserId_valid_returnsTheId() {
        signIn(staff(USER_ID, "ROLE_SALE"));

        assertEquals(USER_ID, CurrentPrincipal.requireUserId());
    }

    @Test
    void expandedAuthorities_superAdmin_reachesAdmin() {
        signIn(staff(USER_ID, "ROLE_SUPER_ADMIN"));

        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), CurrentPrincipal.expandedAuthorities(HIERARCHY));
    }

    @Test
    void expandedAuthorities_noAuthentication_isEmpty() {
        SecurityContextHolder.clearContext();

        assertEquals(Set.of(), CurrentPrincipal.expandedAuthorities(HIERARCHY));
    }

    @Test
    void hasOwnAuthority_ignoresTheHierarchy() {
        signIn(staff(USER_ID, "ROLE_SUPER_ADMIN"));

        assertTrue(CurrentPrincipal.hasOwnAuthority("ROLE_SUPER_ADMIN"));
        assertFalse(CurrentPrincipal.hasOwnAuthority("ROLE_ADMIN"), "ADMIN is reached only through the hierarchy");
    }

    @Test
    void hasOwnAuthority_noAuthentication_isFalse() {
        SecurityContextHolder.clearContext();

        assertFalse(CurrentPrincipal.hasOwnAuthority("ROLE_SUPER_ADMIN"));
    }

    private static void assertFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class, CurrentPrincipal::requireUserId);
        assertEquals("Principal does not expose a user id.", error.getMessage());
    }

    private static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Authentication staff(String userId, String... roles) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(userId), null, roles(roles));
    }

    private static List<SimpleGrantedAuthority> roles(String... roles) {
        return Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
    }

    private record Principal(String userId) implements AuthenticatedUser {

        @Override
        public String getUserId() {
            return userId;
        }
    }
}
