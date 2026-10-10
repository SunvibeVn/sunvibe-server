package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory token buckets, one per key.
 * <p>
 * Counters live in this JVM only: with several application instances behind a
 * load balancer each instance enforces its own limit. Move the buckets to a
 * shared store (e.g. Redis) before scaling out.
 */
@Component
public class TokenBucketRateLimiter {

    /** Buckets idle this long are full again, so dropping them changes nothing. */
    private static final Duration EVICT_AFTER_IDLE = Duration.ofHours(2);

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Takes one request from {@code key}'s allowance.
     *
     * @return 0 if the request is allowed, otherwise the seconds to wait before retrying
     */
    public long tryConsume(String key, int capacity, Duration window, Instant now) {
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, now)).tryConsume(capacity, window, now);
    }

    @Scheduled(fixedDelayString = "PT10M")
    public void evictIdleBuckets() {
        evictIdle(Instant.now());
    }

    int evictIdle(Instant now) {
        int before = buckets.size();
        buckets.values().removeIf(bucket -> bucket.idleSince(now).compareTo(EVICT_AFTER_IDLE) > 0);
        return before - buckets.size();
    }

    int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private double tokens;
        private Instant lastRefill;

        private Bucket(int capacity, Instant now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        synchronized long tryConsume(int capacity, Duration window, Instant now) {
            double tokensPerNano = (double) capacity / window.toNanos();
            long elapsedNanos = Math.max(0, Duration.between(lastRefill, now).toNanos());
            tokens = Math.min(capacity, tokens + elapsedNanos * tokensPerNano);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            double nanosUntilNextToken = (1 - tokens) / tokensPerNano;
            return Math.max(1, (long) Math.ceil(nanosUntilNextToken / 1_000_000_000d));
        }

        synchronized Duration idleSince(Instant now) {
            return Duration.between(lastRefill, now);
        }
    }
}
