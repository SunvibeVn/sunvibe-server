package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The photo store failed or timed out (survey-media spec 4.4 step 2, A-18): 503 MEDIA_STORE_UNAVAILABLE; the same
 * request may be sent again.
 */
public class MediaStoreUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaStoreUnavailableException() {
        super("Photo storage is unavailable. Try again later.");
    }
}
