package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.security.AuthenticatedUser;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter - SurveyViewerResolver
 * <p>
 * The viewer of the authenticated staff member (survey-schedule spec 5.4): the roles are expanded through the role
 * hierarchy (SUPER_ADMIN reaches ADMIN), then the lead queue's one mapping decides OWN or ALL. Anything unexpected
 * fails closed (500), never with a guessed viewer.
 */
@Component
public class SurveyViewerResolver {

    private final SurveyLeadPort leads;
    private final RoleHierarchy roleHierarchy;

    public SurveyViewerResolver(SurveyLeadPort leads, RoleHierarchy roleHierarchy) {
        this.leads = leads;
        this.roleHierarchy = roleHierarchy;
    }

    public SurveyViewer current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || user.getUserId() == null || user.getUserId().isBlank()) {
            // Unreachable after the JWT filter and @PreAuthorize
            throw new IllegalStateException("Principal does not expose a user id.");
        }
        Set<String> roles = roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities()).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return leads.viewer(user.getUserId(), roles);
    }
}
