package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The instance has no room for another photo body or read (survey-media spec A-3, A-25): 503 MEDIA_SERVER_BUSY with
 * {@code Retry-After} in seconds.
 */
public class MediaServerBusyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public MediaServerBusyException(long retryAfterSeconds) {
        super("The server is busy. Try again in a moment.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
