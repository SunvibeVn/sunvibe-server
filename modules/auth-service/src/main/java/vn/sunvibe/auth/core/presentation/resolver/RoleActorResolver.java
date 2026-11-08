package vn.sunvibe.auth.core.presentation.resolver;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.shared.security.CurrentPrincipal;

/**
 * The actor of a role change: the caller's own account. Anything unexpected fails closed (500), never with a guessed
 * actor.
 */
@Component
public class RoleActorResolver {

    private static final String ROLE_SUPER_ADMIN = "ROLE_" + RoleName.SUPER_ADMIN.name();

    /**
     * SUPER_ADMIN is read from the caller's own authorities, not through the role hierarchy, which only widens ADMIN
     * rules.
     */
    public RoleActor current() {
        // Unreachable after the JWT filter and @PreAuthorize when it fails
        String userId = CurrentPrincipal.requireUserId();
        return new RoleActor(userId, CurrentPrincipal.hasOwnAuthority(ROLE_SUPER_ADMIN));
    }

    /**
     * @param userId     the caller's user id
     * @param superAdmin the caller holds SUPER_ADMIN itself
     */
    public record RoleActor(String userId, boolean superAdmin) {
    }
}
