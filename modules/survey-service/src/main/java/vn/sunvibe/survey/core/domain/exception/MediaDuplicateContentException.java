package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * The partial unique backstop of survey-media spec A-16 (a) refused a second selected photo with the same hash in a
 * checklist: answered 409 MEDIA_DUPLICATE_CONTENT, never 500. The message holds no hash.
 */
public class MediaDuplicateContentException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaDuplicateContentException(Throwable cause) {
        super("A selected photo with the same content exists in the checklist.", cause);
    }
}
