package vn.sunvibe.auth.core.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.converter.v1.AuthV1Converter;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordForgotSmsRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.v1.PasswordResetStartResponse;
import vn.sunvibe.auth.core.shared.service.SessionCookieService;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * The two-step password reset (auth-browser-session spec §3.1 rows 7-9): a code by e-mail or SMS, then the code with
 * the new password. Starting a reset answers the same shape whether or not an account exists. Completing it ends every
 * session of the account and starts none: the user signs in again with the new password. All three are public.
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "Auth password reset")
@RequiredArgsConstructor
public class AuthV1PasswordResetController {

    private final PasswordForgotUseCase passwordForgotUseCase;
    private final PasswordResetUseCase passwordResetUseCase;
    private final AuthConverter authConverter;
    private final AuthV1Converter authV1Converter;
    private final SessionCookieService sessionCookies;

    /**
     * POST /api/v1/auth/password-reset/email
     * <p>
     * E-mails a reset code and its token when the address is the verified e-mail of an account.
     */
    @PostMapping("/email")
    @Operation(operationId = "authPasswordResetByEmail",
            summary = "Start a password reset by e-mail (public)",
            description = "E-mails a reset code with its token to a verified account address. The answer is the "
                    + "same whether or not an account exists and never contains the reset token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset started (or nothing to do; same answer)",
                    content = @Content(schema = @Schema(implementation = PasswordResetStartResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on email; "
                    + "MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, for example the e-mail hand-off failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PasswordResetStartResponse> startByEmail(@Valid @RequestBody PasswordForgotRequest request) {
        return ResponseEntity.ok(authV1Converter.toPasswordResetStartResponse(
                passwordForgotUseCase.execute(authConverter.toPasswordForgotCommand(request))));
    }

    /**
     * POST /api/v1/auth/password-reset/sms
     * <p>
     * Texts a reset code to the phone number of an account; the answer carries the reset token.
     */
    @PostMapping("/sms")
    @Operation(operationId = "authPasswordResetBySms",
            summary = "Start a password reset by SMS (public)",
            description = "Texts a reset code to the account's phone number. The answer carries a reset token, a "
                    + "decoy one when no account exists or a code was sent moments ago, so it looks the same.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset started (or nothing to do; same answer)",
                    content = @Content(schema = @Schema(implementation = PasswordResetStartResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on phoneNumber; "
                    + "MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, for example the SMS hand-off failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PasswordResetStartResponse> startBySms(@Valid @RequestBody PasswordForgotSmsRequest request) {
        return ResponseEntity.ok(authV1Converter.toPasswordResetStartResponse(
                passwordForgotUseCase.executeBySms(new PasswordForgotSmsCommand(request.phoneNumber()))));
    }

    /**
     * POST /api/v1/auth/password-reset/complete
     * <p>
     * Checks the code and sets the new password; every session of the account ends.
     */
    @PostMapping("/complete")
    @Operation(operationId = "authPasswordResetComplete",
            summary = "Complete a password reset (public)",
            description = "Checks the code of the reset token and sets the new password. Ends every session of the "
                    + "account and clears the refresh cookie; no new session is started.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password reset; clears the refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Clears the refresh cookie")),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED; CODE_INCORRECT; "
                    + "CODE_ATTEMPTS_EXCEEDED; RESET_TOKEN_INVALID; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "ACCOUNT_DISABLED: the account is locked or terminated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> complete(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetUseCase.verifyOtpAndResetPassword(authConverter.toPasswordResetCommand(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }
}
