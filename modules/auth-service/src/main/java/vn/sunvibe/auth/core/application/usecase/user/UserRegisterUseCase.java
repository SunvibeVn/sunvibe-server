package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

/**
 * Self-registration of a new (tenant) account.
 */
public interface UserRegisterUseCase {

    /**
     * Creates a PENDING account, sends the first verification codes and signs
     * the user in on the registering device.
     *
     * @throws AuthValidationException invalid or already used phone number, e-mail or ID card number
     */
    AuthResult execute(UserRegisterCommand userRegisterCommand);

}
