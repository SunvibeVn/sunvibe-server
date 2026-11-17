package vn.sunvibe.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** How long a consumer's failed row waits before its next attempt (ADR-014 decision 8; E05-S5b S5b-D6). */
public enum OutboxRetryPolicy {

    /** 1, 2, 5, 10, 30, 60, 120, 240 and 480 minutes; {@code DEAD} when the 10th attempt fails (about 16 hours). */
    DEFAULT,

    /** Every 5 minutes without limit; never {@code DEAD}. The row's first recorded failure alerts operations. */
    REMOVAL;

    private static final List<Duration> DEFAULT_BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(2),
            Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofMinutes(60),
            Duration.ofMinutes(120), Duration.ofMinutes(240), Duration.ofMinutes(480));
    private static final Duration REMOVAL_INTERVAL = Duration.ofMinutes(5);

    /**
     * The wait after failed attempt {@code failedAttempt} (1 for the first attempt), or empty when the row goes
     * {@code DEAD}.
     */
    public Optional<Duration> retryAfter(int failedAttempt) {
        if (this == REMOVAL) {
            return Optional.of(REMOVAL_INTERVAL);
        }
        if (failedAttempt > DEFAULT_BACKOFF.size()) {
            return Optional.empty();
        }
        return Optional.of(DEFAULT_BACKOFF.get(Math.max(failedAttempt, 1) - 1));
    }
}
