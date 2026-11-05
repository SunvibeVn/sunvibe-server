package vn.sunvibe.property.core.application.exception;

import java.util.List;

/**
 * A refusal of the intake API. {@code fieldErrors} carries the entries of the body: a gate reason each for
 * {@link IntakeError#INTAKE_GATE_FAILED} (spec 7.4), or the field of a {@link IntakeError#VALIDATION_FAILED}.
 */
public class IntakeRefusedException extends RuntimeException {

    private final IntakeError error;
    private final List<String> fieldErrors;

    public IntakeRefusedException(IntakeError error) {
        this(error, List.of());
    }

    public IntakeRefusedException(IntakeError error, List<String> fieldErrors) {
        super(error.message());
        this.error = error;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public IntakeError error() {
        return error;
    }

    public List<String> fieldErrors() {
        return fieldErrors;
    }
}
