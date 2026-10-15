package vn.sunvibe.shared.exception;

import java.io.Serial;

/**
 * Base class for infrastructure layer exceptions.
 * Used for external system errors (database, email, external APIs, etc).
 */
public abstract class InfrastructureException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected InfrastructureException(String message) {
        super(message);
    }

    protected InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
