package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

/**
 * Signs users in and starts their sessions.
 */
public interface LoginUseCase {

    /**
     * Password login; starts a new session for the device.
     *
     * @throws AccountTemporarilyLockedException too many wrong passwords
     * @throws AuthenticationFailedException     wrong credentials, or the account may not sign in
     */
    AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo);

    /**
     * Starts a new sign-in session for a user whose identity was already
     * proven (registration, password change). Joins the caller's transaction.
     */
    AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo);

}
