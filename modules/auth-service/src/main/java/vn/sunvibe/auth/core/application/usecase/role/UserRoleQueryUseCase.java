package vn.sunvibe.auth.core.application.usecase.role;

import vn.sunvibe.auth.core.application.query.role.UserRolesResult;

/**
 * Reads the roles of one account (role admin API).
 */
public interface UserRoleQueryUseCase {

    /** @throws vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException if no such (non-deleted) account */
    UserRolesResult execute(String userId);

}
