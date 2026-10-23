package vn.sunvibe.acquisition.core.presentation.converter;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.shared.security.AuthenticatedUser;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter - LeadViewerConverter
 * <p>
 * Builds the {@link LeadViewer} of the authenticated staff member (acquisition-owner-lead-queue spec 5.5). The roles
 * are first expanded through the role hierarchy, so SUPER_ADMIN counts as ADMIN. Anything unexpected fails closed with
 * an {@link IllegalStateException} (a 500), never with a guessed viewer.
 */
@Component
public class LeadViewerConverter {

    private final LeadAccessPolicy accessPolicy = new LeadAccessPolicy();

    /**
     * acquisition-owner-lead-queue BR-1: SALE_LEADER, SALES_MANAGER and ADMIN see every lead, a SALE only the leads
     * assigned to them. A person with both SALE and a leader role sees every lead ({@link LeadAccessPolicy}).
     */
    public LeadViewer toViewer(Authentication authentication, RoleHierarchy roleHierarchy) {
        String userId = actorId(authentication);
        Set<String> roles = roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities()).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        LeadAccess access = accessPolicy.accessFor(roles)
                // Unreachable behind @PreAuthorize; fails closed rather than guess what the caller may see
                .orElseThrow(() -> new IllegalStateException("Caller has no role that may read owner leads."));
        return new LeadViewer(userId, access);
    }

    /** The user id of the authenticated staff member, for the history of a territory change. */
    public String actorId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId();
        }
        // Unreachable after the JWT filter and @PreAuthorize; fails closed (500) rather than guess the caller
        throw new IllegalStateException("Principal does not expose a user id.");
    }
}
