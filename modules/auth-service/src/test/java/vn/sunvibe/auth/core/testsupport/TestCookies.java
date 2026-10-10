package vn.sunvibe.auth.core.testsupport;

import org.springframework.test.util.ReflectionTestUtils;
import vn.sunvibe.auth.core.shared.service.CookieService;

/** A {@link CookieService} configured like application.yml, without Spring. */
public final class TestCookies {

    public static final String ACCESS_COOKIE = "cookie-jwt";
    public static final String REFRESH_COOKIE = "cookie-jwt-rf";

    private TestCookies() {
    }

    public static CookieService cookieService() {
        CookieService cookieService = new CookieService();
        ReflectionTestUtils.setField(cookieService, "jwtCookieName", ACCESS_COOKIE);
        ReflectionTestUtils.setField(cookieService, "refreshTokenCookieName", REFRESH_COOKIE);
        ReflectionTestUtils.setField(cookieService, "accessTokenExpirationMinutes", 15L);
        ReflectionTestUtils.setField(cookieService, "refreshTokenExpirationMs", 604_800_000L);
        return cookieService;
    }
}
