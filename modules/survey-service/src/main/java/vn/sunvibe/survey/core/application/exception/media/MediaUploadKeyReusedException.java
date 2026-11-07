package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * The caller's upload key already names another photo body on this checklist (survey-media spec 4.4 step 1, A-5): 409
 * IDEMPOTENCY_KEY_REUSED on {@code uploadKey}.
 */
public class MediaUploadKeyReusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaUploadKeyReusedException() {
        super("This upload key was already used for another photo.");
    }

    public String getField() {
        return "uploadKey";
    }
}
