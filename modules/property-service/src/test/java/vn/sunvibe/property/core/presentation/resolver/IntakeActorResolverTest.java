package vn.sunvibe.property.core.presentation.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeViewer;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.shared.security.AuthenticatedUser;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actor of an intake request (property-create intake spec 4.3): the roles are expanded through the role hierarchy
 * before the sale and manager flags and the lead viewer are read, so SUPER_ADMIN acts as ADMIN; anything unexpected
 * fails closed.
 */
class IntakeActorResolverTest {

    private static final String USER_ID = "01J000000000000000000STAFF";

    /** The same hierarchy as SecurityConfig.roleHierarchy() in app: SUPER_ADMIN implies ADMIN. */
    private static final RoleHierarchy REAL_HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .build();

    /** The authorities the resolver handed to the lead port. */
    private final AtomicReference<Set<String>> passed = new AtomicReference<>();
    private final IntakeActorResolver resolver = new IntakeActorResolver(leadPort(), REAL_HIERARCHY);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "ROLE_SALE,          true,  false",
            "ROLE_SALE_LEADER,   true,  true",
            "ROLE_SALES_MANAGER, false, true",
            "ROLE_ADMIN,         false, true",
            "ROLE_SUPER_ADMIN,   false, true"})
    void current_eachStaffRole_setsTheFlags(String role, boolean saleRole, boolean manager) {
        signIn(staff(role));

        IntakeActor actor = resolver.current();

        assertEquals(USER_ID, actor.userId());
        assertEquals(saleRole, actor.saleRole(), "saleRole");
        assertEquals(manager, actor.manager(), "manager");
    }

    @Test
    void current_superAdmin_handsTheExpandedRolesToTheLeadPort() {
        signIn(staff("ROLE_SUPER_ADMIN"));

        resolver.current();

        assertTrue(passed.get().contains("ROLE_ADMIN"), passed.get().toString());
    }

    @Test
    void current_principalNotAnAuthenticatedUser_failsClosed() {
        signIn(UsernamePasswordAuthenticationToken.authenticated("0909123456", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThrows(IllegalStateException.class, resolver::current);
    }

    @Test
    void current_noAuthentication_failsClosed() {
        assertThrows(IllegalStateException.class, resolver::current);
    }

    @Test
    void userId_owner_isTheUserIdOfThePrincipal() {
        signIn(staff("ROLE_CUSTOMER"));

        assertEquals(USER_ID, resolver.userId());
    }

    @Test
    void userId_noAuthentication_failsClosed() {
        assertThrows(IllegalStateException.class, resolver::userId);
    }

    /** Answers viewerFor only, as the resolver calls nothing else; a leader role sees every lead. */
    @SuppressWarnings("unchecked")
    private IntakeLeadPort leadPort() {
        return (IntakeLeadPort) Proxy.newProxyInstance(IntakeLeadPort.class.getClassLoader(),
                new Class<?>[] {IntakeLeadPort.class}, (proxy, method, args) -> {
                    if (method.getName().equals("viewerFor")) {
                        Set<String> roles = (Set<String>) args[1];
                        passed.set(roles);
                        return new IntakeViewer((String) args[0], roles.contains("ROLE_ADMIN"));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
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
