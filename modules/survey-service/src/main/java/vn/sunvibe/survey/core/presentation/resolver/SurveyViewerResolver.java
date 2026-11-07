package vn.sunvibe.survey.core.presentation.resolver;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.security.CurrentPrincipal;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/**
 * SurveyViewerResolver
 * <p>
 * The viewer of the authenticated staff member (survey-schedule spec 5.4): the roles are expanded through the role
 * hierarchy (SUPER_ADMIN reaches ADMIN), then the lead queue's one mapping decides OWN or ALL. Anything unexpected
 * fails closed (500), never with a guessed viewer.
 */
@Component
public class SurveyViewerResolver {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final SurveyLeadPort leads;
    private final RoleHierarchy roleHierarchy;

    public SurveyViewerResolver(SurveyLeadPort leads, RoleHierarchy roleHierarchy) {
        this.leads = leads;
        this.roleHierarchy = roleHierarchy;
    }

    public SurveyViewer current() {
        // Unreachable after the JWT filter and @PreAuthorize when it fails
        String userId = CurrentPrincipal.requireUserId();
        return leads.viewer(userId, CurrentPrincipal.expandedAuthorities(roleHierarchy));
    }

    /** survey-media spec A-23, 7.2 {@code canHold}: the viewer is ADMIN (SUPER_ADMIN through the role hierarchy). */
    public boolean currentCanHold() {
        return CurrentPrincipal.expandedAuthorities(roleHierarchy).contains(ADMIN_AUTHORITY);
    }
}
