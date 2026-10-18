package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
     * <p>
     * Rules with the same {@code bucket} share one allowance per IP: calling any of
     * them uses the same budget (auth-browser-session spec §3.8). Without a bucket
     * the rule has its own allowance.
     *
     * @param path   the path as Spring MVC routes it (a {@code PathPattern})
     * @param bucket optional name of the shared allowance; null when the rule has its own
     */
    public record Rule(String method, String path, int limit, Duration window, String bucket) {

        /** Bound from configuration; with a second constructor Spring Boot needs to be told which one. */
        @ConstructorBinding
        public Rule {
            if (method == null || path == null || limit < 1 || window == null || window.isNegative() || window.isZero()
                    || (bucket != null && bucket.isBlank())) {
                throw new IllegalArgumentException("Invalid rate limit rule: " + method + " " + path
                        + " limit=" + limit + " window=" + window + " bucket=" + bucket);
            }
        }

        /** A rule with its own allowance. */
        public Rule(String method, String path, int limit, Duration window) {
            this(method, path, limit, window, null);
        }

        /** The key of this rule's allowance, before the client IP is appended. */
        public String bucketKey() {
            return bucket != null ? bucket : method + " " + path;
        }
    }
}
