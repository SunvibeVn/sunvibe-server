package vn.sunvibe.common.exception;

import java.io.Serial;

/**
 * Base class for validation exceptions.
 * Used for input validation errors with field-level information.
 */
public abstract class ValidationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String fieldName;

    protected ValidationException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
