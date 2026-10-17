package vn.sunvibe.acquisition.core.presentation.exception;

import java.io.Serial;

/**
 * The request body of the public intake form is larger than {@link #MAX_BODY_BYTES} (acquisition-owner-intake spec
 * 5.1: the form is small, so a bigger body is refused before it is parsed). Answered with 413
 * {@code PAYLOAD_TOO_LARGE}.
 */
public class PayloadTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 16 KB: far above any real form, small enough that a flood of large bodies costs little. */
    public static final int MAX_BODY_BYTES = 16 * 1024;

    public static final String MESSAGE = "Request body is larger than 16 KB.";

    public PayloadTooLargeException() {
        super(MESSAGE);
    }
}
