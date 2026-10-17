package vn.sunvibe.auth.core.shared.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The narrow refresh cookie of the browser session (spec §3.5, W10-4). */
class SessionCookieServiceTest {

    private static final long SEVEN_DAYS_MS = 604_800_000L;

    private final SessionCookieService service =
            new SessionCookieService(new RefreshCookieProperties("sv_refresh", true), SEVEN_DAYS_MS);

    @Test
    void build_defaults_setsNarrowPathStrictHttpOnlySecureSevenDays() {
        String cookie = service.build("tok").toString();

        assertTrue(cookie.contains("sv_refresh=tok"), cookie);
        assertTrue(cookie.contains("Path=/api/v1/auth/session"), cookie);
        assertTrue(cookie.contains("Max-Age=604800"), cookie);
        assertTrue(cookie.contains("; Secure"), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
        assertFalse(cookie.contains("Domain="), cookie);
    }

    @Test
    void build_secureOff_omitsSecure() {
        SessionCookieService lan =
                new SessionCookieService(new RefreshCookieProperties("sv_refresh", false), SEVEN_DAYS_MS);

        String cookie = lan.build("tok").toString();

        assertFalse(cookie.contains("Secure"), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
    }

    @Test
    void clear_sameNameAndPathWithMaxAgeZero() {
        String cookie = service.clear().toString();

        assertTrue(cookie.startsWith("sv_refresh=;"), cookie);
        assertTrue(cookie.contains("Path=/api/v1/auth/session"), cookie);
        assertTrue(cookie.contains("Max-Age=0"), cookie);
        assertFalse(cookie.contains("Domain="), cookie);
        assertTrue(cookie.contains("; Secure"), cookie);
        assertTrue(cookie.contains("; HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
    }

    /** spec §3.5: the prod cookie name {@code __Secure-sv_refresh} is set and cleared with {@code Secure}. */
    @Test
    void buildAndClear_prodSecureName_carrySecure() {
        SessionCookieService prod =
                new SessionCookieService(new RefreshCookieProperties("__Secure-sv_refresh", true), SEVEN_DAYS_MS);

        String built = prod.build("tok").toString();
        String cleared = prod.clear().toString();

        assertTrue(built.startsWith("__Secure-sv_refresh=tok;"), built);
        assertTrue(built.contains("; Secure"), built);
        assertTrue(cleared.startsWith("__Secure-sv_refresh=;"), cleared);
        assertTrue(cleared.contains("; Secure"), cleared);
    }

    @Test
    void readAll_twoCookiesWithTheName_returnsBothInOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", SessionCookieService.PATH + "/refresh");
        request.setCookies(new Cookie("sv_refresh", "planted"), new Cookie("other", "x"),
                new Cookie("sv_refresh", "mine"));

        assertEquals(List.of("planted", "mine"), service.readAll(request));
    }

    @Test
    void readAll_noCookie_returnsEmptyList() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", SessionCookieService.PATH + "/refresh");

        assertEquals(List.of(), service.readAll(request));
    }
}
