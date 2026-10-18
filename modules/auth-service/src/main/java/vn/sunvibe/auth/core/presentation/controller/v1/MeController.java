package vn.sunvibe.auth.core.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.LogoutUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordChangeUseCase;
import vn.sunvibe.auth.core.application.usecase.user.UserGetInfoUseCase;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.converter.v1.AuthV1Converter;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordChangeRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.VerificationConfirmRequest;
import vn.sunvibe.auth.core.presentation.dto.response.v1.CodeSentResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.MeResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.SessionResponse;
import vn.sunvibe.auth.core.shared.service.SessionCookieService;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * The signed-in user's own account (auth-browser-session spec §3.1 rows 10-16): profile, phone and e-mail
 * verification, password change and signing out every device. Every endpoint needs the access token as
 * {@code Authorization: Bearer}; the security chain answers 401/403 itself with its own body. Each one calls a use
 * case that reads the current user from the security context.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MeController {

    private static final String SECURITY_ERROR = "#/components/schemas/SecurityError";

    private final UserGetInfoUseCase userGetInfoUseCase;
    private final ContactVerificationUseCase contactVerificationUseCase;
    private final PasswordChangeUseCase passwordChangeUseCase;
    private final LogoutUseCase logoutUseCase;
    private final AuthConverter authConverter;
    private final AuthV1Converter authV1Converter;
    private final SessionCookieService sessionCookies;

    /**
     * GET /api/v1/me
     * <p>
     * The signed-in user's own profile.
     */
    @GetMapping
    @Operation(operationId = "getMe",
            summary = "The signed-in user's own profile",
            description = "Never cached: Cache-Control: no-store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The profile",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the account no longer exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(authV1Converter.toMeResponse(userGetInfoUseCase.execute()));
    }

    /**
     * POST /api/v1/me/verification/phone/send-code
     * <p>
     * Texts a 6-digit code to the account's phone number.
     */
    @PostMapping("/verification/phone/send-code")
    @Operation(operationId = "meSendPhoneCode",
            summary = "Send a phone verification code by SMS",
            description = "Texts a code to the account's phone number. A PENDING account becomes ACTIVE once the "
                    + "code is confirmed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code sent",
                    content = @Content(schema = @Schema(implementation = CodeSentResponse.class))),
            @ApiResponse(responseCode = "400", description = "ALREADY_VERIFIED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.CODE_LIMITS_OR_RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, for example the SMS hand-off failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CodeSentResponse> sendPhoneCode() {
        return ResponseEntity.ok(authV1Converter.toCodeSentResponse(
                contactVerificationUseCase.sendCode(VerificationChannel.PHONE)));
    }

    /**
     * POST /api/v1/me/verification/phone/confirm-code
     * <p>
     * Checks the code and marks the phone number verified.
     */
    @PostMapping("/verification/phone/confirm-code")
    @Operation(operationId = "meConfirmPhoneCode",
            summary = "Confirm the phone verification code",
            description = "Marks the phone number verified; a PENDING account becomes ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Phone number verified"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on code; CODE_INCORRECT, "
                    + "CODE_EXPIRED, CODE_ATTEMPTS_EXCEEDED, ALREADY_VERIFIED; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> confirmPhoneCode(@Valid @RequestBody VerificationConfirmRequest request) {
        contactVerificationUseCase.confirm(VerificationChannel.PHONE, request.code());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/me/verification/email/send-code
     * <p>
     * E-mails a 6-digit code to the account's e-mail address.
     */
    @PostMapping("/verification/email/send-code")
    @Operation(operationId = "meSendEmailCode",
            summary = "Send an e-mail verification code",
            description = "E-mails a code to the account's e-mail address.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code sent",
                    content = @Content(schema = @Schema(implementation = CodeSentResponse.class))),
            @ApiResponse(responseCode = "400", description = "NO_EMAIL or ALREADY_VERIFIED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.CODE_LIMITS_OR_RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, for example the e-mail hand-off failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CodeSentResponse> sendEmailCode() {
        return ResponseEntity.ok(authV1Converter.toCodeSentResponse(
                contactVerificationUseCase.sendCode(VerificationChannel.EMAIL)));
    }

    /**
     * POST /api/v1/me/verification/email/confirm-code
     * <p>
     * Checks the code and marks the e-mail address verified.
     */
    @PostMapping("/verification/email/confirm-code")
    @Operation(operationId = "meConfirmEmailCode",
            summary = "Confirm the e-mail verification code",
            description = "Marks the e-mail address verified.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail verified"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on code; CODE_INCORRECT, "
                    + "CODE_EXPIRED, CODE_ATTEMPTS_EXCEEDED, ALREADY_VERIFIED; "
                    + "MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> confirmEmailCode(@Valid @RequestBody VerificationConfirmRequest request) {
        contactVerificationUseCase.confirm(VerificationChannel.EMAIL, request.code());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/me/password/change
     * <p>
     * Changes the password, ends every session of the account and starts a new one for this device.
     */
    @PostMapping("/password/change")
    @Operation(operationId = "meChangePassword",
            summary = "Change the password",
            description = "Ends every session of the account and starts a new one for this device: the access token "
                    + "in the body, the refresh token in the HttpOnly refresh cookie (Path=/api/v1/auth/session). "
                    + "A wrong current password is a field error on currentPassword, never a 401.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed; sets the new refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "The refresh cookie"),
                    content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (bean validation, a wrong current "
                    + "password on currentPassword, or a new password equal to the current one); "
                    + "MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SessionResponse> changePassword(@Valid @RequestBody PasswordChangeRequest request,
                                                          HttpServletRequest httpRequest) {
        AuthResult result = passwordChangeUseCase.execute(authConverter.toPasswordChangeCommand(request, httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.build(result.refreshToken()).toString())
                .body(authV1Converter.toSessionResponse(result, authV1Converter.accessTokenTtl()));
    }

    /**
     * POST /api/v1/me/sessions/logout-all
     * <p>
     * Ends every session of the signed-in user, on all devices.
     */
    @PostMapping("/sessions/logout-all")
    @Operation(operationId = "meLogoutAll",
            summary = "Sign out every device",
            description = "Revokes every session of the account and clears this device's refresh cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Signed out everywhere; clears the refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Clears the refresh cookie")),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(ref = SECURITY_ERROR))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> logoutAll() {
        logoutUseCase.executeAllDevices();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }
}
