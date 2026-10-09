package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import vn.sunvibe.common.time.AppClock;

import java.io.IOException;
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
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    static final String MESSAGE = "Too many requests. Please try again later.";

    private final RateLimitProperties properties;
    private final TokenBucketRateLimiter rateLimiter;

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

        log.warn("Rate limit exceeded: {} {} from {}", rule.method(), rule.path(), clientIp);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":%d,\"message\":\"%s\",\"errorTime\":\"%s\"}"
                .formatted(HttpStatus.TOO_MANY_REQUESTS.value(), MESSAGE, AppClock.currentUTC()));
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
