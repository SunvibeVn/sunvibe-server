package vn.sunvibe.auth.core.presentation.converter.auth;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordChangeRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;

@Component
public class AuthConverter {

    public LoginCommand toLoginCommand(LoginRequest loginRequest) {
        return LoginCommand.builder()
                .username(loginRequest.username())
                .password(loginRequest.password())
                .build();
    }

    public LoginCommand toLoginCommand(UserRegisterRequest userRegisterRequest) {
        return LoginCommand.builder()
                .username(userRegisterRequest.username())
                .password(userRegisterRequest.password())
                .build();
    }

    public PasswordForgotCommand toPasswordForgotCommand(PasswordForgotRequest passwordForgotRequest) {
        return PasswordForgotCommand.builder()
                .email(passwordForgotRequest.email())
                .build();
    }

    public PasswordChangeCommand toPasswordChangeCommand(PasswordChangeRequest passwordChangeRequest) {
        return PasswordChangeCommand.builder()
                .currentPassword(passwordChangeRequest.currentPassword())
                .newPassword(passwordChangeRequest.newPassword())
                .build();
    }
}
