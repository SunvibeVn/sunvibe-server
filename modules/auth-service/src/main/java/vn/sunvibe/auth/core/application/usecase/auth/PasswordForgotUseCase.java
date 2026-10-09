package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;

public interface PasswordForgotUseCase {

    PasswordForgotResponse execute(PasswordForgotCommand passwordForgotCommand);

}
