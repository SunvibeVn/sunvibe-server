package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

/**
 * Self-registration of a new (tenant) account.
 */
public interface UserRegisterUseCase {

    /**
     * Creates the account and signs the user in on the registering device. With a
     * registration token (phone verified first) the account is ACTIVE; without one it
     * is PENDING and gets its first SMS code.
     *
     * @throws AuthValidationException invalid or already used phone number, e-mail or ID card number
     * @throws vn.sunvibe.auth.core.application.exception.auth.VerificationException the registration token is
     *                                 missing (when required), unknown, expired, used or for another phone number
     */
    AuthResult execute(UserRegisterCommand userRegisterCommand);

}
