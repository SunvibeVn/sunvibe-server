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
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
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
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordChangeResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordResetResponse;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.common.response.ApiResponse;

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

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest httpRequest) {
        // Create command
        LoginCommand loginCommand = authConverter.toLoginCommand(loginRequest);
        // Login user, starting a new session for this device
        AuthResult result = loginUseCase.execute(loginCommand, authConverter.toClientInfo(httpRequest));

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

    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(@Valid @RequestBody PasswordChangeRequest passwordChangeRequest, HttpServletRequest httpRequest) {

        // Change user password (this also signs out every device)
        PasswordChangeCommand passwordChangeCommand = authConverter.toPasswordChangeCommand(passwordChangeRequest);
        PasswordChangeResponse passwordChangeResponse = passwordChangeUseCase.execute(passwordChangeCommand);

        // Sign this device back in with a fresh session
        LoginCommand loginCommand = LoginCommand.builder()
                .username(passwordChangeResponse.username()).password(passwordChangeRequest.newPassword()).build();
        AuthResult result = loginUseCase.execute(loginCommand, authConverter.toClientInfo(httpRequest));

        return withTokens(result, AuthMessage.CHANGE_PASSWORD_SUCCESS);
    }

    // Step 1: Request password reset
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<PasswordForgotResponse>> forgotPassword(@Valid @RequestBody PasswordForgotRequest passwordForgotRequest) {

        log.info("Forgot password request from: {}", passwordForgotRequest.email());
        // Create command
        PasswordForgotCommand passwordForgotCommand = authConverter.toPasswordForgotCommand(passwordForgotRequest);
        // Send OTP via email, return password reset token
        PasswordForgotResponse response = passwordForgotUseCase.execute(passwordForgotCommand);

        return ResponseEntity.ok(ApiResponse.<PasswordForgotResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Password reset email sent.")
                .result(response).build());
    }

    // Step 2: Verify OTP + Reset password (signs out every device; the user then logs in again)
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> resetPassword(@Valid @RequestBody PasswordResetRequest passwordResetRequest) {

        log.info("Reset password request received");

        PasswordResetResponse response = passwordResetUseCase.verifyOtpAndResetPassword(passwordResetRequest);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshTokenCookie().toString())
                .body(ApiResponse.<PasswordResetResponse>builder().code(200).message("Password reset successfully").result(response).build());
    }

    private ResponseEntity<ApiResponse<LoginResponse>> withTokens(AuthResult result, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.accessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(ApiResponse.<LoginResponse>builder().code(HttpStatus.OK.value()).message(message).result(result.loginResponse()).build());
    }

    private ResponseEntity<ApiResponse<Void>> withClearedTokens(String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshTokenCookie().toString())
                .body(ApiResponse.<Void>builder().code(HttpStatus.OK.value()).message(message).build());
    }

}
