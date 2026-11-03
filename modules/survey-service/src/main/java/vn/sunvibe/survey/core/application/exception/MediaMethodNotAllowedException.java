package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/** A content read is GET only (survey-media spec 7.1, A-30): HEAD answers 405 METHOD_NOT_ALLOWED with Allow: GET. */
public class MediaMethodNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaMethodNotAllowedException() {
        super("Only GET reads a photo.");
    }
}
