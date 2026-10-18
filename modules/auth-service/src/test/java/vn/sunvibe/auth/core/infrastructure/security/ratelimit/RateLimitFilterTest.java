package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.shared.logging.CorrelationId;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private static final RateLimitProperties.Rule LOGIN =
            new RateLimitProperties.Rule("POST", "/api/v1/auth/login", 3, Duration.ofMinutes(1));

    /** A rule outside {@code /api/v1/}: none is configured today, but the filter keeps the older body for one. */
    private static final RateLimitProperties.Rule UNVERSIONED =
            new RateLimitProperties.Rule("POST", "/api/unversioned/action", 3, Duration.ofMinutes(1));

    private static final RateLimitProperties.Rule V1 =
            new RateLimitProperties.Rule("POST", "/api/v1/public/owner-intake-requests", 10, Duration.ofHours(1));

    private static final String ALLOWED_ORIGIN = "https://app.example";
    private static final String CORRELATION_ID = "req-12345678";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RateLimitFilter.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        filterLogger.addAppender(logs);
        filterLogger.setLevel(Level.WARN);
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
    }

    @AfterEach
    void releaseLogs() {
        filterLogger.detachAppender(logs);
        logs.stop();
        MDC.remove(CorrelationId.MDC_KEY);
    }

    private static CorsConfigurationSource corsSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of(ALLOWED_ORIGIN));
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Idempotent-Replayed", "Retry-After"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private RateLimitFilter filter(boolean enabled) {
        return new RateLimitFilter(new RateLimitProperties(enabled, List.of(LOGIN, V1, UNVERSIONED)), new TokenBucketRateLimiter(),
                JsonMapper.builder().build(), corsSource());
    }

    private static MockHttpServletResponse sendV1(RateLimitFilter filter, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", V1.path());
        request.setRemoteAddr("203.0.113.7");
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static RateLimitFilter exhaustV1(RateLimitFilter filter) throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, sendV1(filter, null).getStatus());
        }
        return filter;
    }

    private static MockHttpServletResponse send(RateLimitFilter filter, String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void answers429WithRetryAfterOnceTheLimitIsReached() throws Exception {
        RateLimitFilter filter = filter(true);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus());
        }

        MockHttpServletResponse limited = send(filter, "POST", "/api/v1/auth/login", "203.0.113.7");

        assertEquals(429, limited.getStatus());
        assertEquals("20", limited.getHeader(HttpHeaders.RETRY_AFTER));
        assertTrue(limited.getContentAsString().contains("\"code\":\"RATE_LIMITED\""));
        assertTrue(limited.getContentAsString().contains(RateLimitFilter.MESSAGE));
    }

    /** Spring MVC decodes and normalises the path before routing, so the limit must too. */
    @Test
    void encodedOrDenormalisedPathsShareTheLimit() throws Exception {
        RateLimitFilter filter = filter(true);
        send(filter, "POST", "/api/v1/auth/log%69n", "203.0.113.7");
        send(filter, "POST", "/api/v1/auth/login;jsessionid=x", "203.0.113.7");
        send(filter, "POST", "/api/v1//auth/login", "203.0.113.7");

        assertEquals(429, send(filter, "POST", "/api/v1/auth/%6C%6F%67%69%6E", "203.0.113.7").getStatus());
    }

    @Test
    void otherClientsAreNotAffected() throws Exception {
        RateLimitFilter filter = filter(true);
        for (int i = 0; i < 4; i++) {
            send(filter, "POST", "/api/v1/auth/login", "203.0.113.7");
        }

        assertEquals(200, send(filter, "POST", "/api/v1/auth/login", "198.51.100.1").getStatus());
    }

    @Test
    void pathsAndMethodsWithoutARuleAreNeverLimited() throws Exception {
        RateLimitFilter filter = filter(true);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, send(filter, "GET", "/api/v1/auth/login", "203.0.113.7").getStatus());
            assertEquals(200, send(filter, "POST", "/api/v1/me", "203.0.113.7").getStatus());
        }
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        RateLimitFilter filter = filter(false);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus());
        }
    }

    @Test
    void v1Path_eleventhRequest_answers429WithErrorResponseBody() throws Exception {
        RateLimitFilter filter = exhaustV1(filter(true));

        MockHttpServletResponse limited = sendV1(filter, null);

        assertEquals(429, limited.getStatus());
        assertNotNull(limited.getHeader(HttpHeaders.RETRY_AFTER));
        assertEquals("application/json;charset=UTF-8", limited.getContentType());
        String body = limited.getContentAsString();
        assertEquals("{\"code\":\"RATE_LIMITED\",\"message\":\"" + RateLimitFilter.MESSAGE
                + "\",\"errors\":[],\"correlationId\":\"" + CORRELATION_ID + "\"}", body);
        assertFalse(body.contains("errorTime"));
        assertFalse(body.contains("\"code\":429"));
    }

    @Test
    void v1Path_limited_carriesCorsHeadersForAnAllowedOrigin() throws Exception {
        RateLimitFilter filter = exhaustV1(filter(true));

        MockHttpServletResponse limited = sendV1(filter, ALLOWED_ORIGIN);

        assertEquals(429, limited.getStatus());
        assertEquals(ALLOWED_ORIGIN, limited.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", limited.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        String exposed = limited.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        assertNotNull(exposed);
        assertTrue(exposed.contains("X-Correlation-Id"));
        assertTrue(exposed.contains("Idempotent-Replayed"));
        assertTrue(exposed.contains("Retry-After"));
    }

    @Test
    void v1Path_limited_disallowedOriginGetsNoAllowOriginHeader() throws Exception {
        RateLimitFilter filter = exhaustV1(filter(true));

        MockHttpServletResponse limited = sendV1(filter, "https://evil.example");

        assertEquals(429, limited.getStatus());
        assertNull(limited.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void v1Path_limited_logsPathAndCorrelationIdButNotTheIp() throws Exception {
        RateLimitFilter filter = exhaustV1(filter(true));

        sendV1(filter, null);

        assertEquals(1, logs.list.size());
        String message = logs.list.get(0).getFormattedMessage();
        assertTrue(message.contains(V1.path()));
        assertTrue(message.contains(CORRELATION_ID));
        assertFalse(message.contains("203.0.113.7"));
    }

    @Test
    void unversionedPath_limited_keepsTheOldBodyAndStillLogsTheIp() throws Exception {
        RateLimitFilter filter = filter(true);
        for (int i = 0; i < 3; i++) {
            send(filter, "POST", UNVERSIONED.path(), "203.0.113.7");
        }

        MockHttpServletResponse limited = send(filter, "POST", UNVERSIONED.path(), "203.0.113.7");

        assertEquals(429, limited.getStatus());
        assertTrue(limited.getContentAsString().contains("\"code\":429"));
        assertTrue(limited.getContentAsString().contains("errorTime"));
        assertEquals(1, logs.list.size());
        assertTrue(logs.list.get(0).getFormattedMessage().contains("203.0.113.7"));
    }

    // ==================== named buckets (auth-browser-session spec §3.8; W10-11) ====================

    /** A second path naming the same bucket as {@link #V1_LOGIN_PAIR}; only the bucket matters to the filter. */
    private static final RateLimitProperties.Rule PEER_PAIR =
            new RateLimitProperties.Rule("POST", "/api/v1/test/peer", 10, Duration.ofMinutes(1), "login");

    private static final RateLimitProperties.Rule V1_LOGIN_PAIR =
            new RateLimitProperties.Rule("POST", "/api/v1/auth/login", 10, Duration.ofMinutes(1), "login");

    private RateLimitFilter pairFilter() {
        return new RateLimitFilter(new RateLimitProperties(true, List.of(PEER_PAIR, V1_LOGIN_PAIR)),
                new TokenBucketRateLimiter(), JsonMapper.builder().build(), corsSource());
    }

    private static MockHttpServletResponse sendFrom(RateLimitFilter filter, String path, String origin)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.7");
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** W10-11: two rules naming one bucket share one allowance per IP. */
    @Test
    void pair_sharesOneBucket() throws Exception {
        RateLimitFilter filter = pairFilter();
        for (int i = 0; i < 5; i++) {
            assertEquals(200, send(filter, "POST", PEER_PAIR.path(), "203.0.113.7").getStatus(), "peer " + i);
            assertEquals(200, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus(), "v1 " + i);
        }

        assertEquals(429, send(filter, "POST", PEER_PAIR.path(), "203.0.113.7").getStatus());
        assertEquals(429, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus());
        assertEquals(200, send(filter, "POST", "/api/v1/auth/login", "198.51.100.1").getStatus(), "per IP");
    }

    @Test
    void pair_rulesWithoutBucket_keepSeparateBuckets() throws Exception {
        RateLimitProperties.Rule peer = new RateLimitProperties.Rule("POST", "/api/v1/test/peer", 1, Duration.ofMinutes(1));
        RateLimitProperties.Rule v1 = new RateLimitProperties.Rule("POST", "/api/v1/auth/login", 1, Duration.ofMinutes(1));
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, List.of(peer, v1)),
                new TokenBucketRateLimiter(), JsonMapper.builder().build(), corsSource());

        assertEquals(200, send(filter, "POST", "/api/v1/test/peer", "203.0.113.7").getStatus());
        assertEquals(200, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus());
        assertNull(peer.bucket());
    }

    /** Spring MVC routes /api/v1/auth/log%69n to login, so the limiter must count it (known-issues 2026-10-17). */
    @Test
    void v1Login_percentEncodedPath_isCounted() throws Exception {
        RateLimitFilter filter = pairFilter();
        for (int i = 0; i < 10; i++) {
            assertEquals(200, send(filter, "POST", "/api/v1/auth/log%69n", "203.0.113.7").getStatus());
        }

        assertEquals(429, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus());
    }

    /**
     * Known-issues 2026-10-17: the path is decoded as UTF-8 the way MVC and Security decode it, never with the
     * charset the client names, so no Content-Type can make the limiter see another path than the controller.
     */
    @Test
    void v1Login_clientCharset_doesNotChangeTheMatchedPath() throws Exception {
        RateLimitFilter filter = pairFilter();
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/log%69n");
            request.setRemoteAddr("203.0.113.7");
            request.setCharacterEncoding("UTF-16");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        assertEquals(429, send(filter, "POST", "/api/v1/auth/login", "203.0.113.7").getStatus());
    }

    /** Spec §3.4, §3.7: the v1 429 is the RATE_LIMITED ErrorResponse and carries CORS for an allowed origin. */
    @Test
    void v1Path_limited_answersRatelimitedBody() throws Exception {
        RateLimitFilter filter = pairFilter();
        for (int i = 0; i < 10; i++) {
            send(filter, "POST", PEER_PAIR.path(), "203.0.113.7");
        }

        MockHttpServletResponse limited = sendFrom(filter, "/api/v1/auth/login", ALLOWED_ORIGIN);

        assertEquals(429, limited.getStatus());
        assertNotNull(limited.getHeader(HttpHeaders.RETRY_AFTER));
        assertEquals("{\"code\":\"RATE_LIMITED\",\"message\":\"" + RateLimitFilter.MESSAGE
                + "\",\"errors\":[],\"correlationId\":\"" + CORRELATION_ID + "\"}", limited.getContentAsString());
        assertEquals(ALLOWED_ORIGIN, limited.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", limited.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals(1, logs.list.size());
        String message = logs.list.get(0).getFormattedMessage();
        assertTrue(message.contains("/api/v1/auth/login"), message);
        assertFalse(message.contains("203.0.113.7"), message);
    }

    @Test
    void rule_blankBucket_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitProperties.Rule("POST", "/api/v1/auth/login", 10, Duration.ofMinutes(1), " "));
    }
}
