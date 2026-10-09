package vn.sunvibe.auth.core.presentation.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.common.dto.response.error.BaseErrorModel;
import vn.sunvibe.common.time.AppClock;

/**
 * Auth-specific error responses. Ordered ahead of the application-wide handler,
 * which would otherwise answer these exceptions with a 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuthExceptionHandler {

    private final CookieService cookieService;

    /**
     * A refresh that cannot succeed ends the session on the client too: 401
     * plus expired cookies, so the app goes straight to the login screen
     * instead of retrying with a dead token.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<BaseErrorModel> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshTokenCookie().toString())
                .body(error(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    /** Wrong credentials, or an account that may not sign in. */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<BaseErrorModel> handleAuthenticationFailed(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    /** Too many wrong passwords: 423 Locked, with the seconds to wait in {@code Retry-After}. */
    @ExceptionHandler(AccountTemporarilyLockedException.class)
    public ResponseEntity<BaseErrorModel> handleTemporarilyLocked(AccountTemporarilyLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.getRetryAfter().toSeconds())))
                .body(error(HttpStatus.LOCKED, e.getMessage()));
    }

    /** Wrong OTP, or a reset token that is unknown, expired, used or burned. */
    @ExceptionHandler({InvalidOtpException.class, PasswordResetTokenException.class})
    public ResponseEntity<BaseErrorModel> handlePasswordResetRejected(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    /** Wrong/expired verification code, or nothing to verify. */
    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<BaseErrorModel> handleVerification(VerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    /** A new code asked for too soon or too often: 429 with the seconds to wait. */
    @ExceptionHandler(VerificationThrottledException.class)
    public ResponseEntity<BaseErrorModel> handleVerificationThrottled(VerificationThrottledException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.getRetryAfter().toSeconds())))
                .body(error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    private static BaseErrorModel error(HttpStatus status, String message) {
        return new BaseErrorModel(status.value(), message, AppClock.currentUTC());
    }
}
