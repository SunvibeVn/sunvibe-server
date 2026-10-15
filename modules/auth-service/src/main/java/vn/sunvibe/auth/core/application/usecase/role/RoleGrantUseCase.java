package vn.sunvibe.auth.core.application.usecase.role;

import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;

/**
 * Grants a role to an account (auth-roles US-4, BR-9, BR-10). Idempotent.
 */
public interface RoleGrantUseCase {

    void execute(RoleChangeCommand command);

}
