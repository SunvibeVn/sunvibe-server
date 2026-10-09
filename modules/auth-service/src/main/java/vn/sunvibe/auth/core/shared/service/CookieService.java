package vn.sunvibe.auth.core.shared.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CookieService {

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
                .path("/")
                .maxAge(Duration.ofMinutes(accessTokenExpirationMinutes))
                .build();
    }

    public ResponseCookie buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(refreshTokenCookieName, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/loginResponse")       // Chỉ gửi lên endpoint loginResponse — giới hạn scope
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
    }

    // Cookie rỗng để clear khi logout
    public ResponseCookie clearAccessTokenCookie() {
        return ResponseCookie.from(jwtCookieName, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(refreshTokenCookieName, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/loginResponse")
                .maxAge(0)
                .build();
    }
}
