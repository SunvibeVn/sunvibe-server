package vn.sunvibe.brokerage.core.presentation.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.shared.security.CurrentPrincipal;

/**
 * The staff member who reads: the principal's id, and the roles expanded through the role hierarchy, so SUPER_ADMIN
 * reaches ADMIN. Anything unexpected fails closed, never with a guessed viewer.
 */
@Component
@RequiredArgsConstructor
public class StaffViewerResolver {

    private final RoleHierarchy roleHierarchy;

    public StaffViewer current() {
        return new StaffViewer(CurrentPrincipal.requireUserId(), CurrentPrincipal.expandedAuthorities(roleHierarchy));
    }
}
