package vn.sunvibe.auth.core.shared.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.sunvibe.auth.core.application.exception.auth.OriginNotAllowedException;
import vn.sunvibe.auth.core.infrastructure.security.config.CorsConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The Origin rule of refresh and logout (auth-browser-session spec §3.6, W10-6). */
class RefreshOriginGuardTest {

    private static final String ALLOWED = "http://localhost:3000";

    private final RefreshOriginGuard guard =
            new RefreshOriginGuard(new CorsConfig().corsConfigurationSource(List.of(ALLOWED)));

    @Test
    void missingOrigin_isRefused() {
        assertThrows(OriginNotAllowedException.class, () -> guard.requireAllowedOrigin(refresh(null)));
    }

    @Test
    void nullOrigin_isRefused() {
        assertThrows(OriginNotAllowedException.class, () -> guard.requireAllowedOrigin(refresh("null")));
    }

    @Test
    void foreignOrigin_isRefused() {
        assertThrows(OriginNotAllowedException.class,
                () -> guard.requireAllowedOrigin(refresh("https://evil.sunvibe.vn")));
    }

    /** A same-origin call (the API's own origin) skips CORS, so the guard is what refuses it. */
    @Test
    void apiOwnOrigin_isRefused() {
        assertThrows(OriginNotAllowedException.class,
                () -> guard.requireAllowedOrigin(refresh("http://localhost:8081")));
    }

    @Test
    void allowedOrigin_passes() {
        assertDoesNotThrow(() -> guard.requireAllowedOrigin(refresh(ALLOWED)));
    }

    private static MockHttpServletRequest refresh(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/session/refresh");
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        return request;
    }
}
