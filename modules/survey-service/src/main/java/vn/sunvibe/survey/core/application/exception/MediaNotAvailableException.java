package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The photo cannot take this action (survey-media spec 5.4 content (8), 7.3): 409 MEDIA_NOT_AVAILABLE on
 * {@code uploadedBy} (the caller did not create it) or {@code status} (removed, or not in a state for it).
 */
public class MediaNotAvailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public MediaNotAvailableException(String field) {
        super("This photo is not available for this action.");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
