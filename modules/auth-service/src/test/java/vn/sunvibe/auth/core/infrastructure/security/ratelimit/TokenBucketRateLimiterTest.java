package vn.sunvibe.auth.core.infrastructure.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-10-09T08:00:00Z");
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();

    @Test
    void allowsUpToTheLimitThenAsksToWait() {
        for (int i = 0; i < 10; i++) {
            assertEquals(0, limiter.tryConsume("ip-1", 10, MINUTE, T0), "request " + (i + 1));
        }

        long retryAfter = limiter.tryConsume("ip-1", 10, MINUTE, T0);

        assertEquals(6, retryAfter, "one request's worth refills every 6 seconds");
    }

    @Test
    void refillsOverTheWindow() {
        for (int i = 0; i < 10; i++) {
            limiter.tryConsume("ip-1", 10, MINUTE, T0);
        }

        assertEquals(0, limiter.tryConsume("ip-1", 10, MINUTE, T0.plusSeconds(6)));
        assertTrue(limiter.tryConsume("ip-1", 10, MINUTE, T0.plusSeconds(6)) > 0);
        for (int i = 0; i < 10; i++) {
            assertEquals(0, limiter.tryConsume("ip-1", 10, MINUTE, T0.plus(Duration.ofMinutes(5))), "full again after a quiet period");
        }
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 10; i++) {
            limiter.tryConsume("ip-1", 10, MINUTE, T0);
        }

        assertTrue(limiter.tryConsume("ip-1", 10, MINUTE, T0) > 0);
        assertEquals(0, limiter.tryConsume("ip-2", 10, MINUTE, T0));
    }

    @Test
    void evictsOnlyLongIdleBuckets() {
        limiter.tryConsume("old", 10, MINUTE, T0);
        limiter.tryConsume("recent", 10, MINUTE, T0.plus(Duration.ofHours(2)));

        int evicted = limiter.evictIdle(T0.plus(Duration.ofHours(3)));

        assertEquals(1, evicted);
        assertEquals(1, limiter.size());
    }
}
