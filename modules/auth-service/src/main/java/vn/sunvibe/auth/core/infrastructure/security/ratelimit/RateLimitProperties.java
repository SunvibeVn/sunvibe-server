package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Per-client-IP request limits for sensitive public endpoints
 * ({@code app.security.rate-limit.*}).
 *
 * @param enabled switch for the whole filter
 * @param rules   one limit per endpoint; requests to other paths are not limited
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * At most {@code limit} requests per {@code window} from one IP to
     * {@code method path}. Short bursts are allowed up to {@code limit}; the
     * allowance then refills evenly over the window (token bucket).
     */
    public record Rule(String method, String path, int limit, Duration window) {

        public Rule {
            if (method == null || path == null || limit < 1 || window == null || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("Invalid rate limit rule: " + method + " " + path
                        + " limit=" + limit + " window=" + window);
            }
        }
    }
}
