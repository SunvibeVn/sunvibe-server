package vn.sunvibe.auth.core.presentation.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.common.dto.response.error.BaseErrorModel;
import vn.sunvibe.common.time.AppClock;

/**
 * Auth-specific error responses. Ordered ahead of the application-wide handler,
 * which would otherwise answer these application exceptions with a 500.
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
                .body(new BaseErrorModel(HttpStatus.UNAUTHORIZED.value(), e.getMessage(), AppClock.currentUTC()));
    }
}
