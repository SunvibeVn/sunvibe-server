package vn.sunvibe.auth.core.shared.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import vn.sunvibe.auth.core.application.exception.auth.OriginNotAllowedException;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * The Origin rule of the endpoints that read the refresh cookie (auth-browser-session spec §3.6): the request must
 * carry an {@code Origin} on the CORS allow-list. CORS already refuses a present, not allowed origin; this guard also
 * refuses a missing one and the API's own origin, which skip CORS. SameSite=Strict does not stop a sibling
 * {@code *.sunvibe.vn} host, and an empty-body POST needs no preflight, so without it such a host could end or rotate
 * the session. Callers run it before the use case, so a refused request changes nothing.
 */
@Slf4j
@Component
public class RefreshOriginGuard {

    /** Longest rejected origin written to the log; an origin is client input. */
    private static final int MAX_LOGGED_ORIGIN = 100;

    private final CorsConfigurationSource corsConfigurationSource;

    public RefreshOriginGuard(@Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /** Throws {@link OriginNotAllowedException} unless the request's {@code Origin} is on the CORS allow-list. */
    public void requireAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        CorsConfiguration configuration = corsConfigurationSource.getCorsConfiguration(request);
        if (origin == null || configuration == null || configuration.checkOrigin(origin) == null) {
            log.warn("Session request refused: origin not allowed (path={}, origin={}, correlationId={})",
                    request.getRequestURI(), loggable(origin), CorrelationId.current());
            throw new OriginNotAllowedException();
        }
    }

    /** The origin without control characters and cut to a bounded length, so a crafted header cannot forge lines. */
    private static String loggable(String origin) {
        if (origin == null) {
            return "<none>";
        }
        String cleaned = origin.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() > MAX_LOGGED_ORIGIN ? cleaned.substring(0, MAX_LOGGED_ORIGIN) + "..." : cleaned;
    }
}
