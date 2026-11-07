package vn.sunvibe.acquisition.core.presentation.resolver;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.shared.security.CurrentPrincipal;

/**
 * LeadViewerResolver
 * <p>
 * Builds the {@link LeadViewer} of the authenticated staff member (acquisition-owner-lead-queue spec 5.5). The roles
 * are first expanded through the role hierarchy, so SUPER_ADMIN counts as ADMIN. Anything unexpected fails closed with
 * an {@link IllegalStateException} (a 500), never with a guessed viewer.
 */
@Component
public class LeadViewerResolver {

    private final LeadAccessPolicy accessPolicy = new LeadAccessPolicy();
    private final RoleHierarchy roleHierarchy;

    public LeadViewerResolver(RoleHierarchy roleHierarchy) {
        this.roleHierarchy = roleHierarchy;
    }

    /**
     * acquisition-owner-lead-queue BR-1: SALE_LEADER, SALES_MANAGER and ADMIN see every lead, a SALE only the leads
     * assigned to them. A person with both SALE and a leader role sees every lead ({@link LeadAccessPolicy}).
     */
    public LeadViewer current() {
        String userId = CurrentPrincipal.requireUserId();
        LeadAccess access = accessPolicy.accessFor(CurrentPrincipal.expandedAuthorities(roleHierarchy))
                // Unreachable behind @PreAuthorize; fails closed rather than guess what the caller may see
                .orElseThrow(() -> new IllegalStateException("Caller has no role that may read owner leads."));
        return new LeadViewer(userId, access);
    }

    /** The user id of the authenticated staff member, for the history of a territory change. */
    public String actorId() {
        return CurrentPrincipal.requireUserId();
    }
}
