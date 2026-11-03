package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/** Survey photo uploads are off (survey-media spec A-1): 409 MEDIA_UPLOAD_DISABLED on create and the content upload. */
public class MediaUploadDisabledException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaUploadDisabledException() {
        super("Photo uploads are switched off.");
    }
}
