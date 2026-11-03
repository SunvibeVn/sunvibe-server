package vn.sunvibe.survey.core.presentation.filter;

import java.io.Serial;

/**
 * A survey-media JSON body is larger than {@link #MAX_BODY_BYTES} (spec A-11, SV-053): thrown by the counting stream
 * of {@link MediaJsonBodyLimitFilter} while the body is read and answered 413 {@code PAYLOAD_TOO_LARGE}.
 */
public class MediaBodyTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** spec A-11: 16 KiB, far above any create, removal, hold or release body. */
    public static final int MAX_BODY_BYTES = 16 * 1024;

    public static final String MESSAGE = "Request body is larger than 16 KiB.";

    public MediaBodyTooLargeException() {
        super(MESSAGE);
    }
}
