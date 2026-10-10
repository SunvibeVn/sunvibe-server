package vn.sunvibe.auth.core.presentation.converter.auth;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.auth.core.testsupport.TestCookies;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthConverterTest {

    private final CookieService cookieService = TestCookies.cookieService();
    private final AuthConverter converter = new AuthConverter(cookieService);

    private static final AuthResult RESULT = new AuthResult("access-token", "refresh-secret", "0912345678", null,
            Set.of("TENANT"), false, false);

    @Test
    void toLoginResponse_carriesTheAccessTokenButNeverTheRefreshToken() {
        LoginResponse response = converter.toLoginResponse(RESULT, "ok");

        assertEquals("access-token", response.accessToken());
        assertEquals(15 * 60, response.expiresIn());
        assertEquals("JWT", response.tokenType());
        assertEquals("ok", response.message());
        assertFalse(response.toString().contains("refresh-secret"));
    }

    /** The refresh token travels only in an HttpOnly, Secure cookie scoped to the auth endpoints. */
    @Test
    void refreshCookieIsScopedAndHttpOnly() {
        String refreshCookie = cookieService.buildRefreshTokenCookie(RESULT.refreshToken()).toString();

        assertTrue(refreshCookie.startsWith(TestCookies.REFRESH_COOKIE + "=refresh-secret"));
        assertTrue(refreshCookie.contains("Path=/api/auth"));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertTrue(refreshCookie.contains("Secure"));
    }
}
