package vn.sunvibe.property.core.domain.exception;

/** A row an index refused, because a concurrent command stored its twin first (property-create intake spec 5.3). */
public class IntakeConflictException extends RuntimeException {

    public IntakeConflictException(Throwable cause) {
        super("A concurrent command changed the intake.", cause);
    }
}
