package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The photo does not exist, or its lead is not visible to the caller (survey-media spec 4.3): one 404 body for
 * both, so a caller learns nothing about leads they cannot see.
 */
public class MediaNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaNotFoundException() {
        super("Photo not found.");
    }
}
