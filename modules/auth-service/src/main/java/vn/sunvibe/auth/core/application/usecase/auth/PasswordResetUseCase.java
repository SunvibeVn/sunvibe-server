package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;

import java.time.Instant;

/**
 * Step 2 of the password reset: checks the one-time code and sets the new
 * password. Every session of the account is ended; no new one is started.
 */
public interface PasswordResetUseCase {

    /**
     * @return when the password was changed
     * @throws InvalidOtpException           wrong code (counted against the token)
     * @throws PasswordResetTokenException   unknown, expired, used or burned token
     * @throws AuthenticationFailedException the account is locked or disabled
     */
    Instant verifyOtpAndResetPassword(PasswordResetCommand command);

}
