package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/** survey-media spec 4.7, BR-8: a submission references the photo, so it cannot be removed (409 MEDIA_REFERENCED). */
public class MediaReferencedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaReferencedException() {
        super("A submission uses this photo, so it cannot be removed.");
    }
}
