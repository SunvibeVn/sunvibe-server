package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private static final RateLimitProperties.Rule LOGIN =
            new RateLimitProperties.Rule("POST", "/api/auth/login", 3, Duration.ofMinutes(1));

    private RateLimitFilter filter(boolean enabled) {
        return new RateLimitFilter(new RateLimitProperties(enabled, List.of(LOGIN)), new TokenBucketRateLimiter());
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
            assertEquals(200, send(filter, "POST", "/api/auth/login", "203.0.113.7").getStatus());
        }

        MockHttpServletResponse limited = send(filter, "POST", "/api/auth/login", "203.0.113.7");

        assertEquals(429, limited.getStatus());
        assertEquals("20", limited.getHeader(HttpHeaders.RETRY_AFTER));
        assertTrue(limited.getContentAsString().contains("\"code\":429"));
        assertTrue(limited.getContentAsString().contains(RateLimitFilter.MESSAGE));
    }

    /** Spring MVC decodes and normalises the path before routing, so the limit must too. */
    @Test
    void encodedOrDenormalisedPathsShareTheLimit() throws Exception {
        RateLimitFilter filter = filter(true);
        send(filter, "POST", "/api/auth/log%69n", "203.0.113.7");
        send(filter, "POST", "/api/auth/login;jsessionid=x", "203.0.113.7");
        send(filter, "POST", "/api//auth/login", "203.0.113.7");

        assertEquals(429, send(filter, "POST", "/api/auth/%6C%6F%67%69%6E", "203.0.113.7").getStatus());
    }

    @Test
    void otherClientsAreNotAffected() throws Exception {
        RateLimitFilter filter = filter(true);
        for (int i = 0; i < 4; i++) {
            send(filter, "POST", "/api/auth/login", "203.0.113.7");
        }

        assertEquals(200, send(filter, "POST", "/api/auth/login", "198.51.100.1").getStatus());
    }

    @Test
    void pathsAndMethodsWithoutARuleAreNeverLimited() throws Exception {
        RateLimitFilter filter = filter(true);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, send(filter, "GET", "/api/auth/login", "203.0.113.7").getStatus());
            assertEquals(200, send(filter, "POST", "/api/user/query/my-info", "203.0.113.7").getStatus());
        }
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        RateLimitFilter filter = filter(false);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, send(filter, "POST", "/api/auth/login", "203.0.113.7").getStatus());
        }
    }
}
