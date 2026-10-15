package vn.sunvibe.auth.core.application.usecase.role;

import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;

/**
 * Revokes a role from an account and ends every session of it (auth-roles US-4, BR-9, BR-11). Idempotent.
 */
public interface RoleRevokeUseCase {

    void execute(RoleChangeCommand command);

}
