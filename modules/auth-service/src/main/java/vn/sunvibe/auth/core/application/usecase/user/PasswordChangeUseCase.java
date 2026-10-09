package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

/**
 * The signed-in user changes their password.
 */
public interface PasswordChangeUseCase {

    /**
     * Changes the password, ends every session of the user and starts a fresh
     * one for the requesting device.
     *
     * @throws AuthValidationException wrong current password, or the new one equals it
     */
    AuthResult execute(PasswordChangeCommand passwordChangeCommand);

}
