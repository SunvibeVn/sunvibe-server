package vn.sunvibe.auth.core.shared.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Builds, clears and reads the refresh cookie of the browser session. It is HttpOnly (not readable by scripts),
 * SameSite=Strict, host-only (no Domain, so no sibling host receives it) and only sent to
 * {@link #PATH}.
 */
@Service
public class SessionCookieService {

    /** The refresh and logout endpoints are the only ones that read the cookie. */
    public static final String PATH = "/api/v1/auth/session";

    private final RefreshCookieProperties properties;
    private final Duration idleLifetime;

    public SessionCookieService(RefreshCookieProperties properties,
                                @Value("${app.jwt.jwtRefreshExpirationMs}") long idleMs) {
        this.properties = properties;
        this.idleLifetime = Duration.ofMillis(idleMs);
    }

    /** The refresh cookie carrying {@code token}; it lives as long as the refresh token's idle lifetime. */
    public ResponseCookie build(String token) {
        return cookie(token, idleLifetime);
    }

    /** An expired, empty refresh cookie with the same name and path: makes the browser drop it. */
    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    /**
     * Every value sent under the cookie name, in header order; empty when there is none. More than one value means a
     * sibling host planted a second cookie (auth-browser-session spec §3.5), so callers must not just take the first.
     */
    public List<String> readAll(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return List.of();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .toList();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
