package vn.sunvibe.acquisition.core.presentation.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.hierarchicalroles.NullRoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.shared.security.AuthenticatedUser;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How a staff member's roles become a lead viewer (acquisition-owner-lead-queue spec 5.5,
 * acquisition-owner-lead-queue BR-1). The roles are expanded through the role hierarchy first, so SUPER_ADMIN sees
 * what ADMIN sees; anything the resolver does not recognise fails closed.
 */
class LeadViewerResolverTest {

    private static final String USER_ID = "01J00000000000000000000SALE";

    /** The same hierarchy as SecurityConfig.roleHierarchy() in app: SUPER_ADMIN implies ADMIN. */
    private static final RoleHierarchy REAL_HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .build();

    private final LeadViewerResolver resolver = new LeadViewerResolver(REAL_HIERARCHY);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void current_sale_isOwn() {
        signIn(staff("ROLE_SALE"));

        assertEquals(new LeadViewer(USER_ID, LeadAccess.OWN), resolver.current());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN"})
    void current_leaderManagerOrAdmin_isAll(String role) {
        signIn(staff(role));

        assertEquals(new LeadViewer(USER_ID, LeadAccess.ALL), resolver.current());
    }

    @Test
    void current_superAdminWithTheRealHierarchy_isAll() {
        signIn(staff("ROLE_SUPER_ADMIN"));

        assertEquals(LeadAccess.ALL, resolver.current().access());
    }

    /** Without the hierarchy SUPER_ADMIN matches nothing: the resolver must expand, not read the raw roles. */
    @Test
    void current_superAdminWithoutHierarchy_failsClosed() {
        signIn(staff("ROLE_SUPER_ADMIN"));
        LeadViewerResolver withoutHierarchy = new LeadViewerResolver(new NullRoleHierarchy());

        assertThrows(IllegalStateException.class, withoutHierarchy::current);
    }

    @Test
    void current_saleAndSaleLeader_isAll() {
        signIn(staff("ROLE_SALE", "ROLE_SALE_LEADER"));

        assertEquals(LeadAccess.ALL, resolver.current().access());
    }

    @Test
    void current_ownerOnly_throwsIllegalState() {
        signIn(staff("ROLE_OWNER"));

        IllegalStateException error = assertThrows(IllegalStateException.class, resolver::current);

        assertFalse(error.getMessage().contains(USER_ID), error.getMessage());
    }

    @Test
    void current_principalNotAnAuthenticatedUser_throwsIllegalState() {
        signIn(UsernamePasswordAuthenticationToken.authenticated("0909123456", null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThrows(IllegalStateException.class, resolver::current);
    }

    @Test
    void current_noAuthentication_throwsIllegalState() {
        assertThrows(IllegalStateException.class, resolver::current);
    }

    @Test
    void actorId_staff_isTheUserIdOfThePrincipal() {
        signIn(staff("ROLE_ADMIN"));

        assertEquals(USER_ID, resolver.actorId());
    }

    @Test
    void actorId_noAuthentication_throwsIllegalState() {
        assertThrows(IllegalStateException.class, resolver::actorId);
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
