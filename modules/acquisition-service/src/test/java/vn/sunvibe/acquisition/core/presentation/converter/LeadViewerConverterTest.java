package vn.sunvibe.acquisition.core.presentation.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.hierarchicalroles.NullRoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * what ADMIN sees; anything the converter does not recognise fails closed.
 */
class LeadViewerConverterTest {

    private static final String USER_ID = "01J00000000000000000000SALE";

    /** The same hierarchy as SecurityConfig.roleHierarchy() in app: SUPER_ADMIN implies ADMIN. */
    private static final RoleHierarchy REAL_HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .build();

    private final LeadViewerConverter converter = new LeadViewerConverter();

    @Test
    void toViewer_sale_isOwn() {
        LeadViewer viewer = converter.toViewer(staff("ROLE_SALE"), REAL_HIERARCHY);

        assertEquals(new LeadViewer(USER_ID, LeadAccess.OWN), viewer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN"})
    void toViewer_leaderManagerOrAdmin_isAll(String role) {
        LeadViewer viewer = converter.toViewer(staff(role), REAL_HIERARCHY);

        assertEquals(new LeadViewer(USER_ID, LeadAccess.ALL), viewer);
    }

    @Test
    void toViewer_superAdminWithTheRealHierarchy_isAll() {
        LeadViewer viewer = converter.toViewer(staff("ROLE_SUPER_ADMIN"), REAL_HIERARCHY);

        assertEquals(LeadAccess.ALL, viewer.access());
    }

    /** Without the hierarchy SUPER_ADMIN matches nothing: the converter must expand, not read the raw roles. */
    @Test
    void toViewer_superAdminWithoutHierarchy_failsClosed() {
        assertThrows(IllegalStateException.class,
                () -> converter.toViewer(staff("ROLE_SUPER_ADMIN"), new NullRoleHierarchy()));
    }

    @Test
    void toViewer_saleAndSaleLeader_isAll() {
        LeadViewer viewer = converter.toViewer(staff("ROLE_SALE", "ROLE_SALE_LEADER"), REAL_HIERARCHY);

        assertEquals(LeadAccess.ALL, viewer.access());
    }

    @Test
    void toViewer_ownerOnly_throwsIllegalState() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> converter.toViewer(staff("ROLE_OWNER"), REAL_HIERARCHY));

        assertFalse(error.getMessage().contains(USER_ID), error.getMessage());
    }

    @Test
    void toViewer_principalNotAnAuthenticatedUser_throwsIllegalState() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated("0909123456", null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThrows(IllegalStateException.class, () -> converter.toViewer(authentication, REAL_HIERARCHY));
    }

    @Test
    void toViewer_noAuthentication_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> converter.toViewer(null, REAL_HIERARCHY));
    }

    @Test
    void actorId_staff_isTheUserIdOfThePrincipal() {
        assertEquals(USER_ID, converter.actorId(staff("ROLE_ADMIN")));
    }

    @Test
    void actorId_noAuthentication_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> converter.actorId(null));
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
