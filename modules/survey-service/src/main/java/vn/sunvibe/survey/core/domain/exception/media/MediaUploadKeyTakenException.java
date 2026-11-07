package vn.sunvibe.survey.core.domain.exception.media;

import java.io.Serial;

/**
 * The unique key (checklist, uploader, upload key) refused a concurrent twin create (survey-media spec 4.4 step 1):
 * the use case answers with the replay of the stored photo, never 500.
 */
public class MediaUploadKeyTakenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaUploadKeyTakenException(Throwable cause) {
        super("The upload key is already used by this uploader on this checklist.", cause);
    }
}
