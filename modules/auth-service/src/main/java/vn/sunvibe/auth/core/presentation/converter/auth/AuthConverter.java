package vn.sunvibe.auth.core.presentation.converter.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordChangeRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;

/**
 * Converts auth requests to commands; the v1 response bodies come from
 * {@link vn.sunvibe.auth.core.presentation.converter.v1.AuthV1Converter}.
 */
@Component
public class AuthConverter {

    public LoginCommand toLoginCommand(LoginRequest loginRequest) {
        return LoginCommand.builder()
                .username(loginRequest.username())
                .password(loginRequest.password())
                .build();
    }

    public PasswordForgotCommand toPasswordForgotCommand(PasswordForgotRequest passwordForgotRequest) {
        return PasswordForgotCommand.builder()
                .email(passwordForgotRequest.email())
                .build();
    }

    public PasswordChangeCommand toPasswordChangeCommand(PasswordChangeRequest passwordChangeRequest,
                                                         HttpServletRequest httpRequest) {
        return PasswordChangeCommand.builder()
                .currentPassword(passwordChangeRequest.currentPassword())
                .newPassword(passwordChangeRequest.newPassword())
                .clientInfo(toClientInfo(httpRequest))
                .build();
    }

    public PasswordResetCommand toPasswordResetCommand(PasswordResetRequest request) {
        return new PasswordResetCommand(request.getResetToken(), request.getOtpCode(), request.getNewPassword());
    }

    /**
     * The requesting device. The IP is the direct peer address; behind a reverse
     * proxy, set {@code server.forward-headers-strategy} so it is the client's
     * (reading {@code X-Forwarded-For} here would let any client spoof it).
     */
    public ClientInfo toClientInfo(HttpServletRequest httpRequest) {
        return new ClientInfo(httpRequest.getHeader(HttpHeaders.USER_AGENT), httpRequest.getRemoteAddr());
    }

    public TokenRefreshCommand toTokenRefreshCommand(String refreshToken, HttpServletRequest httpRequest) {
        return new TokenRefreshCommand(refreshToken, toClientInfo(httpRequest));
    }
}
