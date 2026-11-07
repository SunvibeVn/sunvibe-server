package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * A content upload declares more than 20 MiB (survey-media spec A-2, 5.4 content (1)): 413 PAYLOAD_TOO_LARGE.
 */
public class MediaContentTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MediaContentTooLargeException() {
        super("The photo is larger than 20 MB.");
    }
}
