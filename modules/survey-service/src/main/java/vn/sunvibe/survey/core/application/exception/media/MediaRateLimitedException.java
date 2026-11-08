package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * A per-user limit of survey-media was reached (survey-media spec A-3, A-25, A-26, A-27): 429 RATE_LIMITED on the field
 * ({@code records}, {@code bytes}, {@code expensive}, {@code bodies}, {@code concurrency} or {@code budget}) with
 * {@code Retry-After} in seconds.
 */
public class MediaRateLimitedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;
    private final long retryAfterSeconds;

    public MediaRateLimitedException(String field, long retryAfterSeconds) {
        super("Too many photo requests. Try again later.");
        this.field = field;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public String getField() {
        return field;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
