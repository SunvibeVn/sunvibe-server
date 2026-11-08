package vn.sunvibe.auth.core.presentation.converter.role;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;
import vn.sunvibe.auth.core.application.query.role.UserRolesResult;
import vn.sunvibe.auth.core.presentation.dto.response.role.UserRolesResponse;
import vn.sunvibe.auth.core.presentation.resolver.RoleActorResolver.RoleActor;

/**
 * Builds role admin commands from the path and the signed-in caller.
 */
@Component
public class RoleConverter {

    /** The actor is the caller, as {@code RoleActorResolver} read it from the signed-in principal. */
    public RoleChangeCommand toCommand(RoleActor actor, String userId, String role) {
        return new RoleChangeCommand(actor.userId(), actor.superAdmin(), userId, role);
    }

    public UserRolesResponse toResponse(UserRolesResult result) {
        return new UserRolesResponse(result.userId(), result.roles());
    }
}
