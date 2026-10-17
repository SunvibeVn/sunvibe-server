package vn.sunvibe.acquisition.core.domain.exception;

import vn.sunvibe.shared.exception.ValidationException;

import java.io.Serial;

/**
 * A value of the owner intake form breaks a domain rule. Carries the name of the field and an English message that
 * never contains the submitted value (acquisition-owner-intake: no personal data in error bodies or logs).
 */
public class InvalidIntakeValueException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidIntakeValueException(String field, String message) {
        super(field, message);
    }

    /**
     * Name of the request field that failed, as the client sent it (for example {@code phone} or
     * {@code askingPrice.unit}).
     */
    public String getField() {
        return getFieldName();
    }
}
