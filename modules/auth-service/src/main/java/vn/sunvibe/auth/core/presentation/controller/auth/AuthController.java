package vn.sunvibe.auth.core.presentation.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.application.usecase.auth.LogoutUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.TokenRefreshUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordChangeUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordChangeRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotSmsRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordResetResponse;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.shared.response.ApiResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Sign-in sessions and passwords: login, token refresh, logout, password
 * change and the two-step password reset (e-mail or SMS).
 * <p>
 * Every endpoint that starts or refreshes a session sets the access-token and
 * refresh-token HttpOnly cookies and returns the access token in the body too.
 */
@Log4j2
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final PasswordChangeUseCase passwordChangeUseCase;
    private final PasswordForgotUseCase passwordForgotUseCase;
    private final PasswordResetUseCase passwordResetUseCase;
    private final TokenRefreshUseCase tokenRefreshUseCase;
    private final LogoutUseCase logoutUseCase;
    private final AuthConverter authConverter;
    private final CookieService cookieService;

    /** Password login; starts a new session for this device. */
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest httpRequest) {
        AuthResult result = loginUseCase.execute(authConverter.toLoginCommand(loginRequest), authConverter.toClientInfo(httpRequest));

        return withTokens(result, AuthMessage.LOGIN_SUCCESS);
    }

    /**
     * Exchanges the refresh-token cookie for a new access token and a new
     * refresh token (rotation). Public: it is called precisely when the access
     * token has expired. On failure answers 401 and clears both cookies.
     */
    @PostMapping(value = "/refresh-token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(HttpServletRequest httpRequest) {
        String refreshToken = cookieService.readRefreshToken(httpRequest);
        AuthResult result = tokenRefreshUseCase.execute(authConverter.toTokenRefreshCommand(refreshToken, httpRequest));

        return withTokens(result, AuthMessage.TOKEN_REFRESH_SUCCESS);
    }

    /** Ends this device's session. Public and idempotent, so an expired access token never blocks signing out. */
    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        logoutUseCase.execute(cookieService.readRefreshToken(httpRequest));

        return withClearedTokens(AuthMessage.LOGOUT_SUCCESS);
    }

    /** Ends every session of the signed-in user, on all devices. */
    @PostMapping(value = "/logout-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        logoutUseCase.executeAllDevices();

        return withClearedTokens(AuthMessage.LOGOUT_ALL_SUCCESS);
    }

    /** Changes the password, signs out every device and starts a fresh session for this one. */
    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(@Valid @RequestBody PasswordChangeRequest passwordChangeRequest, HttpServletRequest httpRequest) {
        AuthResult result = passwordChangeUseCase.execute(authConverter.toPasswordChangeCommand(passwordChangeRequest, httpRequest));

        return withTokens(result, AuthMessage.CHANGE_PASSWORD_SUCCESS);
    }

    /** Step 1 (e-mail): e-mails a reset code and a link carrying the reset token. */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<PasswordForgotResponse>> forgotPassword(@Valid @RequestBody PasswordForgotRequest passwordForgotRequest) {
        log.info("Forgot password request received");
        PasswordForgotResult result = passwordForgotUseCase.execute(authConverter.toPasswordForgotCommand(passwordForgotRequest));

        return forgotPasswordResponse(result);
    }

    /** Step 1 (SMS): texts a reset code to the phone number; the response carries the reset token. */
    @PostMapping("/forgot-password/sms")
    public ResponseEntity<ApiResponse<PasswordForgotResponse>> forgotPasswordBySms(@Valid @RequestBody PasswordForgotSmsRequest request) {
        log.info("Forgot password by SMS request received");
        PasswordForgotResult result = passwordForgotUseCase.executeBySms(new PasswordForgotSmsCommand(request.phoneNumber()));

        return forgotPasswordResponse(result);
    }

    /** Step 2: checks the code and sets the new password. Signs out every device; the user then logs in again. */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> resetPassword(@Valid @RequestBody PasswordResetRequest passwordResetRequest) {
        log.info("Reset password request received");
        Instant changedAt = passwordResetUseCase.verifyOtpAndResetPassword(authConverter.toPasswordResetCommand(passwordResetRequest));

        PasswordResetResponse response = PasswordResetResponse.builder()
                .message(AuthMessage.PASSWORD_RESET_SUCCESS)
                .passwordChangedAt(LocalDateTime.ofInstant(changedAt, ZoneId.systemDefault()))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshTokenCookie().toString())
                .body(ApiResponse.<PasswordResetResponse>builder().code(HttpStatus.OK.value())
                        .message(AuthMessage.PASSWORD_RESET_SUCCESS).result(response).build());
    }

    private ResponseEntity<ApiResponse<PasswordForgotResponse>> forgotPasswordResponse(PasswordForgotResult result) {
        return ResponseEntity.ok(ApiResponse.<PasswordForgotResponse>builder()
                .code(HttpStatus.OK.value())
                .message(result.message())
                .result(authConverter.toPasswordForgotResponse(result))
                .build());
    }

    private ResponseEntity<ApiResponse<LoginResponse>> withTokens(AuthResult result, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.buildAccessTokenCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.<LoginResponse>builder().code(HttpStatus.OK.value()).message(message)
                        .result(authConverter.toLoginResponse(result, message)).build());
    }

    private ResponseEntity<ApiResponse<Void>> withClearedTokens(String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshTokenCookie().toString())
                .body(ApiResponse.<Void>builder().code(HttpStatus.OK.value()).message(message).build());
    }

}
