package vn.sunvibe.listing.core.presentation.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.shared.security.CurrentPrincipal;

/**
 * The staff member who reads or acts: the principal's id, and the roles expanded through the role hierarchy, so
 * SUPER_ADMIN reaches ADMIN. The actor comes from the principal only, never from the request (security.md); anything
 * unexpected fails closed.
 */
@Component
@RequiredArgsConstructor
public class ListingViewerResolver {

    private final RoleHierarchy roleHierarchy;

    public ListingViewer current() {
        return new ListingViewer(CurrentPrincipal.requireUserId(), CurrentPrincipal.expandedAuthorities(roleHierarchy));
    }
}
