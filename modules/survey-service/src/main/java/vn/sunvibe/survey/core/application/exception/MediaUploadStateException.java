package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The photo's upload state refuses the content (survey-media spec 4.4 step 2, 5.4 content (9)): 409 with the code of
 * the kind.
 */
public class MediaUploadStateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The refusal, each with its error code. */
    public enum Kind {
        /** MEDIA_CONTENT_ALREADY_RECEIVED: other bytes were received. */
        CONTENT_ALREADY_RECEIVED,
        /** MEDIA_UPLOAD_IN_PROGRESS: another upload holds the claim. */
        UPLOAD_IN_PROGRESS,
        /** MEDIA_UPLOAD_EXPIRED: no upload was claimed within 60 minutes, or a takeover ended it. */
        UPLOAD_EXPIRED
    }

    private final Kind kind;

    public MediaUploadStateException(Kind kind) {
        super(switch (kind) {
            case CONTENT_ALREADY_RECEIVED -> "This photo already received other bytes.";
            case UPLOAD_IN_PROGRESS -> "This photo is being uploaded.";
            case UPLOAD_EXPIRED -> "This photo waited too long for its bytes. Add it again.";
        });
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
