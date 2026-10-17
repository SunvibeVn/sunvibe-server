package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.time.AppClock;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Answers 429 Too Many Requests when one client IP calls a sensitive endpoint
 * too often (brute-force passwords or OTPs, mass registration, e-mail bombing).
 * <p>
 * Runs before Spring Security so rejected requests cost almost nothing. The IP
 * is the direct peer address: behind a reverse proxy, configure
 * {@code server.forward-headers-strategy} so it is the real client's
 * (trusting {@code X-Forwarded-For} blindly would let clients pick their own key).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String MESSAGE = "Too many requests. Please try again later.";

    /** Paths under it use the ADR-010 error body and never log the client IP; older paths keep their legacy shape. */
    private static final String VERSIONED_API_PREFIX = "/api/v1/";

    private final RateLimitProperties properties;
    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    /** Written by hand: Spring MVC also publishes a CorsConfigurationSource bean, so ours is named explicitly. */
    public RateLimitFilter(RateLimitProperties properties, TokenBucketRateLimiter rateLimiter, ObjectMapper objectMapper,
                           @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.enabled() || matchingRule(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        RateLimitProperties.Rule rule = matchingRule(request).orElseThrow();
        String clientIp = request.getRemoteAddr();
        long retryAfterSeconds = rateLimiter.tryConsume(rule.method() + " " + rule.path() + "|" + clientIp,
                rule.limit(), rule.window(), AppClock.currentUTC());

        if (retryAfterSeconds == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean versionedApi = rule.path().startsWith(VERSIONED_API_PREFIX);
        if (versionedApi) {
            log.warn("Rate limit exceeded: {} {} [{}]", rule.method(), rule.path(), CorrelationId.current());
            // This filter answers before Spring Security's CORS filter, so the browser would not see the 429 otherwise.
            applyCorsHeaders(request, response);
        } else {
            log.warn("Rate limit exceeded: {} {} from {}", rule.method(), rule.path(), clientIp);
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(versionedApi ? versionedBody() : legacyBody());
    }

    private String legacyBody() {
        return "{\"code\":%d,\"message\":\"%s\",\"errorTime\":\"%s\"}"
                .formatted(HttpStatus.TOO_MANY_REQUESTS.value(), MESSAGE, AppClock.currentUTC());
    }

    private String versionedBody() {
        return objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.RATE_LIMITED, MESSAGE, List.of()));
    }

    /** Only an allowed origin gets the headers; a disallowed one must not be answered 403 by the CORS processor. */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        CorsConfiguration corsConfiguration = corsConfigurationSource.getCorsConfiguration(request);
        if (origin == null || corsConfiguration == null || corsConfiguration.checkOrigin(origin) == null) {
            return;
        }
        new DefaultCorsProcessor().processRequest(corsConfiguration, request, response);
    }

    /**
     * Matches on the decoded, normalised path (percent-escapes resolved, {@code ;...}
     * parameters and duplicate slashes removed), the way Spring MVC routes it.
     * Matching the raw URI would let {@code /api/auth/log%69n} skip the limit.
     */
    private Optional<RateLimitProperties.Rule> matchingRule(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return properties.rules().stream()
                .filter(rule -> rule.method().equalsIgnoreCase(request.getMethod()) && rule.path().equals(path))
                .findFirst();
    }
}
