package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * The checklist has its most selected (BR-6: 100) or pending (spec A-3: 20) photos: 409 MEDIA_LIMIT_REACHED on
 * {@code selected} or {@code pending}.
 */
public class MediaLimitReachedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public MediaLimitReachedException(String field) {
        super("The checklist has the most photos allowed.");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
