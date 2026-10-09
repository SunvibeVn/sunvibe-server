package vn.sunvibe.auth.core.shared.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

@Service
public class CookieService {

    /**
     * The refresh token is only sent to the endpoints that consume it
     * ({@code /api/auth/refresh-token}, {@code /api/auth/logout}), never to the rest of the API.
     */
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth";
    private static final String ACCESS_TOKEN_COOKIE_PATH = "/";

    @Value("${app.jwt.expiration.minutes}")
    private long accessTokenExpirationMinutes;

    @Value("${app.jwt.jwtRefreshExpirationMs}")
    private long refreshTokenExpirationMs;

    @Value("${app.jwt.jwtCookieName}")
    private String jwtCookieName;

    @Value("${app.jwt.jwtRefreshCookieName}")
    private String refreshTokenCookieName;

    public ResponseCookie buildAccessTokenCookie(String token) {
        return ResponseCookie.from(jwtCookieName, token)
                .httpOnly(true)          // JS không đọc được → chống XSS
                .secure(true)            // Chỉ gửi qua HTTPS
                .sameSite("Strict")      // Chống CSRF
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(accessTokenTtl())
                .build();
    }

    public ResponseCookie buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(refreshTokenCookieName, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
    }

    // Cookie rỗng để clear khi logout
    public ResponseCookie clearAccessTokenCookie() {
        return ResponseCookie.from(jwtCookieName, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(refreshTokenCookieName, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /** The refresh token sent by the browser, or null when the cookie is absent. */
    public String readRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, refreshTokenCookieName);
        return cookie != null ? cookie.getValue() : null;
    }

    /** How long an access token (and its cookie) stays valid. */
    public Duration accessTokenTtl() {
        return Duration.ofMinutes(accessTokenExpirationMinutes);
    }
}
