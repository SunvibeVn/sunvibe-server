package vn.sunvibe.auth.core.presentation.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.presentation.resolver.RoleActorResolver.RoleActor;
import vn.sunvibe.shared.security.AuthenticatedUser;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The actor of a role change (auth-roles): the caller's own id, and SUPER_ADMIN from the caller's own authorities, so
 * an ADMIN never counts as SUPER_ADMIN; anything unexpected fails closed.
 */
class RoleActorResolverTest {

    private static final String USER_ID = "01J000000000000000000ADMIN";

    private final RoleActorResolver resolver = new RoleActorResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void current_superAdmin_isSuperAdmin() {
        signIn(staff("ROLE_SUPER_ADMIN"));

        assertEquals(new RoleActor(USER_ID, true), resolver.current());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_SALES_MANAGER", "ROLE_SALE"})
    void current_otherRole_isNotSuperAdmin(String role) {
        signIn(staff(role));

        assertEquals(new RoleActor(USER_ID, false), resolver.current());
    }

    @Test
    void current_superAdminAndAdmin_isSuperAdmin() {
        signIn(staff("ROLE_ADMIN", "ROLE_SUPER_ADMIN"));

        assertEquals(new RoleActor(USER_ID, true), resolver.current());
    }

    @Test
    void current_principalNotAnAuthenticatedUser_failsClosed() {
        signIn(UsernamePasswordAuthenticationToken.authenticated("0909123456", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        assertThrows(IllegalStateException.class, resolver::current);
    }

    @Test
    void current_noAuthentication_failsClosed() {
        assertThrows(IllegalStateException.class, resolver::current);
    }

    private static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Authentication staff(String... roles) {
        return UsernamePasswordAuthenticationToken.authenticated(new StaffPrincipal(USER_ID), null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private record StaffPrincipal(String userId) implements AuthenticatedUser {

        @Override
        public String getUserId() {
            return userId;
        }
    }
}
