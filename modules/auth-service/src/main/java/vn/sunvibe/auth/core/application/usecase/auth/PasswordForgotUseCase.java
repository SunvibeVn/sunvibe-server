package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;

/**
 * Step 1 of the password reset: sends a one-time code. The answer never tells
 * whether the e-mail or phone number belongs to an account.
 */
public interface PasswordForgotUseCase {

    /** E-mails a reset code (and reset token) if the address is a verified e-mail of an account. */
    PasswordForgotResult execute(PasswordForgotCommand passwordForgotCommand);

    /**
     * Texts a reset code to the phone number if it belongs to an account. The
     * reset token is returned in the result (it is useless without the SMS code).
     */
    PasswordForgotResult executeBySms(PasswordForgotSmsCommand command);

}
