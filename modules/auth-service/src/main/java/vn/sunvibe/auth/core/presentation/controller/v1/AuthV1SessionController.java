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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.LogoutUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.TokenRefreshUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.converter.v1.AuthV1Converter;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.dto.response.v1.SessionResponse;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.auth.core.shared.service.RefreshOriginGuard;
import vn.sunvibe.auth.core.shared.service.SessionCookieService;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;

/**
 * The browser session (auth-browser-session spec §3.1 rows 1-3): password login, refresh and logout. The access token
 * is returned in the body and sent back as {@code Authorization: Bearer}; the refresh token travels only in the
 * HttpOnly refresh cookie, which the browser sends to {@code /api/v1/auth/session} and nowhere else. The legacy
 * access-token cookie is never set here (ADR-001).
 * <p>
 * All three are public. Refresh and logout identify the session by the cookie alone, so they also require an allowed
 * {@code Origin} ({@link RefreshOriginGuard}), checked before anything is rotated or revoked.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth session")
@RequiredArgsConstructor
public class AuthV1SessionController {

    private final LoginUseCase loginUseCase;
    private final TokenRefreshUseCase tokenRefreshUseCase;
    private final LogoutUseCase logoutUseCase;
    private final AuthConverter authConverter;
    private final AuthV1Converter authV1Converter;
    private final SessionCookieService sessionCookies;
    private final CookieService cookieService;
    private final RefreshOriginGuard originGuard;

    /**
     * POST /api/v1/auth/login
     * <p>
     * Password login; starts a new session for this device.
     */
    @PostMapping("/login")
    @Operation(operationId = "authLogin",
            summary = "Sign in with phone number and password (public)",
            description = "Starts a new session for this device: the access token in the body, the refresh token "
                    + "in the HttpOnly refresh cookie (Path=/api/v1/auth/session).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in; sets the refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "The refresh cookie"),
                    content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on username or password; "
                    + "MALFORMED_REQUEST (unreadable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: unknown phone, wrong password, "
                    + "or an account that may not sign in (one answer for all)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST: the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: too many wrong passwords; "
                    + "Retry-After gives the seconds to wait",
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                 HttpServletRequest httpRequest) {
        AuthResult result = loginUseCase.execute(authConverter.toLoginCommand(request),
                authConverter.toClientInfo(httpRequest));
        return withSession(result);
    }

    /**
     * POST /api/v1/auth/session/refresh
     * <p>
     * Exchanges the refresh cookie for a new access token and a new refresh token (rotation).
     */
    @PostMapping("/session/refresh")
    @SecurityRequirement(name = "refreshCookie")
    @Operation(operationId = "authSessionRefresh",
            summary = "Refresh the session from the refresh cookie (public, allowed Origin only)",
            description = "Rotates the refresh token. Called when the access token has expired, so it takes none. "
                    + "Requires an allowed Origin header; a cookie sent twice is refused and nothing is rotated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A new access token; sets the rotated refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "The rotated refresh cookie"),
                    content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "401", description = "SESSION_EXPIRED: no, unknown, expired, revoked or "
                    + "reused token, an account that may not sign in, or the cookie sent twice; clears the cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Clears the refresh cookie"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ORIGIN_NOT_ALLOWED: no Origin header, or the API's own "
                    + "origin. A present, not allowed Origin gets the CORS filter's plain-text 403 instead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = AuthV1ApiDocs.RATE_LIMITED,
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SessionResponse> refresh(HttpServletRequest httpRequest) {
        originGuard.requireAllowedOrigin(httpRequest);
        List<String> tokens = sessionCookies.readAll(httpRequest);
        if (tokens.size() > 1) {
            // auth-browser-session S-25: a sibling host may have planted a second cookie to slip its own session in;
            // refuse without rotating either one
            log.warn("Refresh refused: session cookie sent twice (correlationId={})", CorrelationId.current());
            throw new InvalidRefreshTokenException(AuthMessage.SESSION_EXPIRED);
        }
        String token = tokens.isEmpty() ? null : tokens.get(0);
        AuthResult result = tokenRefreshUseCase.execute(authConverter.toTokenRefreshCommand(token, httpRequest));
        return withSession(result);
    }

    /**
     * POST /api/v1/auth/session/logout
     * <p>
     * Ends the session of the refresh cookie. Idempotent: 204 also without a cookie or with a dead token.
     */
    @PostMapping("/session/logout")
    @SecurityRequirement(name = "refreshCookie")
    @Operation(operationId = "authSessionLogout",
            summary = "Sign out this device (public, allowed Origin only)",
            description = "Revokes the session of the refresh cookie and clears the cookie. 204 also without a "
                    + "cookie. A cookie sent twice ends both sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Signed out; clears the refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Clears the refresh cookie")),
            @ApiResponse(responseCode = "403", description = "ORIGIN_NOT_ALLOWED: no Origin header, or the API's own "
                    + "origin. A present, not allowed Origin gets the CORS filter's plain-text 403 instead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        originGuard.requireAllowedOrigin(httpRequest);
        List<String> tokens = sessionCookies.readAll(httpRequest);
        if (tokens.size() > 1) {
            // auth-browser-session S-25: end every session sent, the user's and a planted one, so signing out works
            log.warn("Logout: session cookie sent {} times, ending every session sent (correlationId={})",
                    tokens.size(), CorrelationId.current());
        }
        tokens.forEach(logoutUseCase::execute);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }

    private ResponseEntity<SessionResponse> withSession(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.build(result.refreshToken()).toString())
                .body(authV1Converter.toSessionResponse(result, cookieService.accessTokenTtl()));
    }
}
