package vn.sunvibe.auth.core.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationEmailVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationPhoneVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.EmailRegisterUseCase;
import vn.sunvibe.auth.core.application.usecase.user.UserRegisterUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.converter.user.UserConverter;
import vn.sunvibe.auth.core.presentation.converter.v1.AuthV1Converter;
import vn.sunvibe.auth.core.presentation.dto.request.auth.RegistrationEmailSendRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.RegistrationEmailVerifyRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.RegistrationPhoneSendRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.RegistrationPhoneVerifyRequest;
import vn.sunvibe.auth.core.presentation.dto.request.user.EmailRegisterRequest;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.RegistrationTokenResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.CodeSentResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.SessionResponse;
import vn.sunvibe.auth.core.shared.service.SessionCookieService;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Self-registration (auth-browser-session spec §3.1 rows 4-6): "verify first, register later". The email, or with the
 * phone channel open the phone number, is proven with a code, which yields a registration token; the registration then
 * signs the new account in on this device (the access token in the body, the refresh token in the HttpOnly refresh
 * cookie). All of them are public: the person has no account yet.
 */
@RestController
@RequestMapping("/api/v1/auth/registration")
@Tag(name = "Auth registration")
@RequiredArgsConstructor
public class AuthV1RegistrationController {

    private final RegistrationPhoneVerificationUseCase registrationPhoneVerificationUseCase;
    private final UserRegisterUseCase userRegisterUseCase;
    private final RegistrationEmailVerificationUseCase registrationEmailVerificationUseCase;
    private final EmailRegisterUseCase emailRegisterUseCase;
    private final UserConverter userConverter;
    private final AuthConverter authConverter;
    private final AuthV1Converter authV1Converter;
    private final SessionCookieService sessionCookies;

    /**
     * POST /api/v1/auth/registration/email/send-code
     * <p>
     * Mails a 6-digit code to the address. Reads no account, so the answer is the same for every valid address.
     */
    @PostMapping("/email/send-code")
    @Operation(operationId = "authRegistrationEmailSendCode",
            summary = "Send a registration code by email (public)",
            description = "Mails a code that proves the registrant controls this mailbox. The answer does not depend on "
                    + "whether an account holds the address.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code sent",
                    content = @Content(schema = @Schema(implementation = CodeSentResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on email; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.CODE_LIMITS_OR_RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "EMAIL_DELIVERY_FAILED: nothing was kept, try again",
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CodeSentResponse> sendEmailCode(@Valid @RequestBody RegistrationEmailSendRequest request) {
        return ResponseEntity.ok(authV1Converter.toCodeSentResponse(
                registrationEmailVerificationUseCase.sendCode(request.email())));
    }

    /**
     * POST /api/v1/auth/registration/email/verify-code
     * <p>
     * Checks the code. Only after a right code does the answer depend on who holds the address.
     */
    @PostMapping("/email/verify-code")
    @Operation(operationId = "authRegistrationEmailVerifyCode",
            summary = "Check the registration code of a mailbox (public)",
            description = "Answers a registration token for an address no one holds. The right code for an address an "
                    + "account holds is used up and answers 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mailbox proven",
                    content = @Content(schema = @Schema(implementation = RegistrationTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on email or code; CODE_INCORRECT, "
                    + "CODE_EXPIRED, CODE_ATTEMPTS_EXCEEDED; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ACCOUNT_EXISTS: the verified address of an account; "
                    + "EMAIL_NOT_AVAILABLE: this address cannot be used now",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegistrationTokenResponse> verifyEmailCode(
            @Valid @RequestBody RegistrationEmailVerifyRequest request) {
        RegistrationTokenResult result = registrationEmailVerificationUseCase.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(new RegistrationTokenResponse(result.registrationToken(), result.expiresAt()));
    }

    /**
     * POST /api/v1/auth/registration/email
     * <p>
     * Creates the account of a proven mailbox and starts its first session on this device.
     */
    @PostMapping("/email")
    @Operation(operationId = "authRegisterEmail",
            summary = "Register a new account by email (public)",
            description = "Creates the account with the registration token of the proven mailbox and signs it in: the "
                    + "access token in the body, the refresh token in the HttpOnly refresh cookie "
                    + "(Path=/api/v1/auth/session).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered and signed in; sets the refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "The refresh cookie"),
                    content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED; REGISTRATION_TOKEN_INVALID; "
                    + "MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_NOT_AVAILABLE: the address was taken meanwhile",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SessionResponse> registerByEmail(@Valid @RequestBody EmailRegisterRequest request,
                                                           HttpServletRequest httpRequest) {
        AuthResult result = emailRegisterUseCase.execute(
                userConverter.toEmailRegisterCommand(request, authConverter.toClientInfo(httpRequest)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookies.build(result.refreshToken()).toString())
                .body(authV1Converter.toSessionResponse(result, authV1Converter.accessTokenTtl()));
    }

    /**
     * POST /api/v1/auth/registration/phone/send-code
     * <p>
     * Texts a 6-digit code to a phone number that no account uses yet.
     */
    @PostMapping("/phone/send-code")
    @Operation(operationId = "authRegistrationSendCode",
            summary = "Send a registration code by SMS (public)",
            description = "Texts a code that proves the registrant controls this phone number. A number already in "
                    + "use gets no code.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code sent",
                    content = @Content(schema = @Schema(implementation = CodeSentResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on phoneNumber, or on username when "
                    + "the number is already in use; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "AUTH_CHANNEL_DISABLED: sign-up by phone is closed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.CODE_LIMITS_OR_RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, for example the SMS hand-off failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CodeSentResponse> sendCode(@Valid @RequestBody RegistrationPhoneSendRequest request) {
        return ResponseEntity.ok(authV1Converter.toCodeSentResponse(
                registrationPhoneVerificationUseCase.sendCode(request.phoneNumber())));
    }

    /**
     * POST /api/v1/auth/registration/phone/verify-code
     * <p>
     * Checks the code and answers the registration token that proves the phone number.
     */
    @PostMapping("/phone/verify-code")
    @Operation(operationId = "authRegistrationVerifyCode",
            summary = "Check the registration code (public)",
            description = "Answers a registration token to send with the registration.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phone number verified",
                    content = @Content(schema = @Schema(implementation = RegistrationTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on phoneNumber or code; "
                    + "CODE_INCORRECT, CODE_EXPIRED, CODE_ATTEMPTS_EXCEEDED; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "AUTH_CHANNEL_DISABLED: sign-up by phone is closed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegistrationTokenResponse> verifyCode(
            @Valid @RequestBody RegistrationPhoneVerifyRequest request) {
        RegistrationTokenResult result = registrationPhoneVerificationUseCase.verifyCode(request.phoneNumber(),
                request.code());
        return ResponseEntity.ok(new RegistrationTokenResponse(result.registrationToken(), result.expiresAt()));
    }

    /**
     * POST /api/v1/auth/registration
     * <p>
     * Creates the account and starts its first session on this device.
     */
    @PostMapping
    @Operation(operationId = "authRegister",
            summary = "Register a new account (public)",
            description = "Creates the account with the registration token of the verified phone number and signs "
                    + "it in: the access token in the body, the refresh token in the HttpOnly refresh cookie "
                    + "(Path=/api/v1/auth/session).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered and signed in; sets the refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "The refresh cookie"),
                    content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (bean validation, or phone or ID card "
                    + "in use); REGISTRATION_TOKEN_INVALID; MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "AUTH_CHANNEL_DISABLED: sign-up by phone is closed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SessionResponse> register(@Valid @RequestBody UserRegisterRequest request,
                                                    HttpServletRequest httpRequest) {
        AuthResult result = userRegisterUseCase.execute(
                userConverter.toUserRegisterCommand(request, authConverter.toClientInfo(httpRequest)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookies.build(result.refreshToken()).toString())
                .body(authV1Converter.toSessionResponse(result, authV1Converter.accessTokenTtl()));
    }
}
