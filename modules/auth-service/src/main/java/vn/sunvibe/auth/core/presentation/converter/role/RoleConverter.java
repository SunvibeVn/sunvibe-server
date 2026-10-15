package vn.sunvibe.auth.core.presentation.converter.role;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;
import vn.sunvibe.auth.core.application.query.role.UserRolesResult;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.presentation.dto.response.role.UserRolesResponse;
import vn.sunvibe.shared.security.AuthenticatedUser;

/**
 * Builds role admin commands from the path and the signed-in caller.
 */
@Component
public class RoleConverter {

    private static final String ROLE_SUPER_ADMIN = "ROLE_" + RoleName.SUPER_ADMIN.name();

    /**
     * The actor is the caller's own account (user id from the JWT filter's principal). SUPER_ADMIN is read from
     * the caller's own authorities, not through the role hierarchy, which only widens ADMIN rules.
     */
    public RoleChangeCommand toCommand(String userId, String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser actor)) {
            // Unreachable after the JWT filter and @PreAuthorize; fails closed
            throw new IllegalStateException("Principal does not expose a user id");
        }
        boolean actorIsSuperAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_SUPER_ADMIN::equals);
        return new RoleChangeCommand(actor.getUserId(), actorIsSuperAdmin, userId, role);
    }

    public UserRolesResponse toResponse(UserRolesResult result) {
        return new UserRolesResponse(result.userId(), result.roles());
    }
}
