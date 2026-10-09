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
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordChangeUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordChangeRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordChangeResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordResetResponse;
import vn.sunvibe.common.response.ApiResponse;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.auth.core.shared.utils.http.HttpRequestUtils;

@Log4j2
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final PasswordChangeUseCase passwordChangeUseCase;
    private final PasswordForgotUseCase passwordForgotUseCase;
    private final PasswordResetUseCase passwordResetUseCase;
    private final AuthConverter authConverter;

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest httpRequest) {
        // Get fingerprint to create refresh token
        String rawFingerprint = HttpRequestUtils.getRawFingerprint(httpRequest);
        // Create command
        LoginCommand loginCommand = authConverter.toLoginCommand(loginRequest);
        // Login user
        AuthResult result = loginUseCase.execute(loginCommand, rawFingerprint);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, String.valueOf(result.accessCookie())).header(HttpHeaders.SET_COOKIE, String.valueOf(result.refreshCookie())).body((ApiResponse.<LoginResponse>builder().code(HttpStatus.OK.value()).message(AuthMessage.LOGIN_SUCCESS).result(result.loginResponse()).build()));
    }

    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(@Valid @RequestBody PasswordChangeRequest passwordChangeRequest, HttpServletRequest httpRequest) {

        // Change user password
        PasswordChangeCommand passwordChangeCommand = authConverter.toPasswordChangeCommand(passwordChangeRequest);
        PasswordChangeResponse passwordChangeResponse = passwordChangeUseCase.execute(passwordChangeCommand);

        // Get fingerprint to create refresh token
        String rawFingerprint = HttpRequestUtils.getRawFingerprint(httpRequest);
        // Login user after change password
        LoginCommand loginCommand = LoginCommand.builder()
                .username(passwordChangeResponse.username()).password(passwordChangeRequest.newPassword()).build();
        AuthResult result = loginUseCase.execute(loginCommand, rawFingerprint);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, String.valueOf(result.accessCookie())).header(HttpHeaders.SET_COOKIE, String.valueOf(result.refreshCookie())).body((ApiResponse.<LoginResponse>builder().code(HttpStatus.OK.value()).message(AuthMessage.CHANGE_PASSWORD_SUCCESS).result(result.loginResponse()).build()));
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

    // Step 2: Verify OTP + Reset password
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> resetPassword(@Valid @RequestBody PasswordResetRequest passwordResetRequest) {

        log.info("Reset password request with token: {}", passwordResetRequest.getResetToken());

        PasswordResetResponse response = passwordResetUseCase.verifyOtpAndResetPassword(passwordResetRequest);

        return ResponseEntity.ok(ApiResponse.<PasswordResetResponse>builder().code(200).message("Password reset successfully").result(response).build());
    }

}
