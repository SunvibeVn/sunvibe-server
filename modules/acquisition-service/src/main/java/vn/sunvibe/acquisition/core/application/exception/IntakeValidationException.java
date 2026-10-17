package vn.sunvibe.acquisition.core.application.exception;

import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.shared.exception.ValidationException;

import java.io.Serial;

/**
 * A value of the owner intake form was refused: an unknown province or ward, a ward of another province, or a value
 * that breaks a domain rule. Carries the request field (for example {@code wardCode} or {@code consent.accepted})
 * and an English message that never contains the submitted value.
 */
public class IntakeValidationException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IntakeValidationException(String field, String message) {
        super(field, message);
    }

    /** The same field and message as the domain error. */
    public static IntakeValidationException from(InvalidIntakeValueException error) {
        return new IntakeValidationException(error.getField(), error.getMessage());
    }

    /** Name of the request field that failed, as the client sent it. */
    public String getField() {
        return getFieldName();
    }
}
