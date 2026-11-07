package vn.sunvibe.survey.core.presentation.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.shared.security.AuthenticatedUser;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyLeadPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer of a survey request (survey-schedule spec 5.4) and the hold right of survey-media A-23: the roles are
 * expanded through the role hierarchy, so SUPER_ADMIN acts as ADMIN; anything unexpected fails closed.
 */
class SurveyViewerResolverTest {

    private static final String USER_ID = "01J000000000000000000STAFF";

    /** The same hierarchy as SecurityConfig.roleHierarchy() in app: SUPER_ADMIN implies ADMIN. */
    private static final RoleHierarchy REAL_HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .build();

    private final List<String> journal = new ArrayList<>();
    private final SurveyViewerResolver resolver =
            new SurveyViewerResolver(new InMemorySurveyLeadPort(journal), REAL_HIERARCHY);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void current_sale_isOwn() {
        signIn(staff("ROLE_SALE"));

        assertEquals(new SurveyViewer(USER_ID, ViewerAccess.OWN), resolver.current());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"})
    void current_leaderManagerAdminOrSuperAdmin_isAll(String role) {
        signIn(staff(role));

        assertEquals(new SurveyViewer(USER_ID, ViewerAccess.ALL), resolver.current());
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

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_SUPER_ADMIN"})
    void currentCanHold_adminOrSuperAdmin_isTrue(String role) {
        signIn(staff(role));

        assertTrue(resolver.currentCanHold());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SALE", "ROLE_SALE_LEADER", "ROLE_SALES_MANAGER"})
    void currentCanHold_otherStaff_isFalse(String role) {
        signIn(staff(role));

        assertFalse(resolver.currentCanHold());
    }

    @Test
    void currentCanHold_noAuthentication_isFalse() {
        assertFalse(resolver.currentCanHold());
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
