package vn.sunvibe.property.core.presentation.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.shared.security.CurrentPrincipal;

import java.util.Set;

/**
 * The actor of an intake endpoint: the principal's id, and the roles expanded through the role hierarchy, so SUPER_ADMIN
 * reaches ADMIN. Anything unexpected fails closed, never with a guessed actor.
 */
@Component
@RequiredArgsConstructor
public class IntakeActorResolver {

    private static final Set<String> SALE_ROLES = Set.of("ROLE_SALE", "ROLE_SALE_LEADER");
    private static final Set<String> MANAGER_ROLES = Set.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN");

    private final IntakeLeadPort leads;
    private final RoleHierarchy roleHierarchy;

    public IntakeActor current() {
        String userId = userId();
        Set<String> roles = CurrentPrincipal.expandedAuthorities(roleHierarchy);
        boolean saleRole = roles.stream().anyMatch(SALE_ROLES::contains);
        boolean manager = roles.stream().anyMatch(MANAGER_ROLES::contains);
        return new IntakeActor(leads.viewerFor(userId, roles), saleRole, manager);
    }

    /** The signed-in user of an owner endpoint: any account, staff included, which the use case then refuses. */
    public String userId() {
        // Unreachable after the JWT filter and the path rule when it fails
        return CurrentPrincipal.requireUserId();
    }
}
