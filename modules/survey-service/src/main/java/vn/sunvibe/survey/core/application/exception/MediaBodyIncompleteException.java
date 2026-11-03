package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * A photo body ended before its declared length, failed while it was read or passed its deadline (survey-media spec
 * 4.4 step 2): 400 MALFORMED_REQUEST on {@code body}; nothing is stored.
 */
public class MediaBodyIncompleteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaBodyIncompleteException() {
        super("The photo body ended early.");
    }
}
