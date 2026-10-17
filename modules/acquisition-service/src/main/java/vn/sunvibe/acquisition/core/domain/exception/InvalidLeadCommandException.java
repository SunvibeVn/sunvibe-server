package vn.sunvibe.acquisition.core.domain.exception;

import java.io.Serial;

/**
 * A value of a lead command (status change, assignment or note) breaks a domain rule. Carries the name of the field
 * and an English message that never contains the submitted value.
 */
public class InvalidLeadCommandException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidLeadCommandException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Name of the request field that failed, as the client sent it (for example {@code reason}). */
    public String getField() {
        return field;
    }
}
