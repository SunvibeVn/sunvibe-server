package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * The serving kill switch is off (survey-media spec A-24): every content GET answers 503 MEDIA_SERVING_DISABLED with
 * {@code Retry-After}, before any media query.
 */
public class MediaServingDisabledException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** spec A-24: serving comes back with a restart; the client tries again a few minutes later. */
    public static final long RETRY_AFTER_SECONDS = 300;

    public MediaServingDisabledException() {
        super("Photos are not shown for now. Try again later.");
    }
}
