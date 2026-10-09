package vn.sunvibe.auth.core.presentation.converter.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordChangeRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.auth.core.shared.service.CookieService;

/**
 * Converts auth requests to commands and use-case results to response bodies.
 */
@Component
@RequiredArgsConstructor
public class AuthConverter {

    /** Value of {@link LoginResponse#tokenType()}. */
    static final String TOKEN_TYPE = "JWT";

    private final CookieService cookieService;

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

    /** Body of every response that hands out a token pair; the refresh token stays out of it (cookie only). */
    public LoginResponse toLoginResponse(AuthResult result, String message) {
        return LoginResponse.builder()
                .tokenType(TOKEN_TYPE)
                .accessToken(result.accessToken())
                .expiresIn(cookieService.accessTokenTtl().toSeconds())
                .username(result.username())
                .userEmail(result.email())
                .roles(result.roles())
                .phoneVerified(result.phoneVerified())
                .emailVerified(result.emailVerified())
                .message(message)
                .build();
    }

    public PasswordForgotResponse toPasswordForgotResponse(PasswordForgotResult result) {
        return PasswordForgotResponse.builder()
                .message(result.message())
                .resetToken(result.resetToken())
                .expiresAt(result.expiresAt())
                .otpExpirationMinutes(result.codeLifetime().toMinutes())
                .build();
    }
}
