package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;

public interface PasswordForgotUseCase {

    /** E-mails a reset code (and reset token) if the address is a verified e-mail of an account. */
    PasswordForgotResponse execute(PasswordForgotCommand passwordForgotCommand);

    /**
     * Texts a reset code to the phone number if it belongs to an account. The
     * reset token is returned in the response (it is useless without the SMS code).
     */
    PasswordForgotResponse executeBySms(PasswordForgotSmsCommand command);

}
