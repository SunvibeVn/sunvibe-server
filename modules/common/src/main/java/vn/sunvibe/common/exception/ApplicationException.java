package vn.sunvibe.common.exception;

import java.io.Serial;

/**
 * Base class for application layer exceptions.
 * Used for use case errors and application-level business logic failures.
 */
public abstract class ApplicationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
