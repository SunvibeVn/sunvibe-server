package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * A content upload's request cannot be processed (survey-media spec 5.4 content (1), (12), 7.3): 415
 * MALFORMED_REQUEST on {@code Content-Type}, or 400 MALFORMED_REQUEST on {@code Content-Length}, {@code Content-Digest} or
 * {@code body}.
 */
public class MediaRequestMalformedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;
    private final boolean unsupportedMediaType;

    public MediaRequestMalformedException(String field, boolean unsupportedMediaType) {
        super(unsupportedMediaType ? "The request cannot be processed." : "Request body could not be read.");
        this.field = field;
        this.unsupportedMediaType = unsupportedMediaType;
    }

    public String getField() {
        return field;
    }

    /** 415 instead of 400. */
    public boolean isUnsupportedMediaType() {
        return unsupportedMediaType;
    }
}
