package vn.sunvibe.property.core.domain.exception;

/**
 * A unit code the property already has, ignoring case and surrounding spaces (property-create intake BR-8, spec 5.1). The
 * code is not in the message: it can come from a survey.
 */
public class UnitCodeTakenException extends RuntimeException {

    public UnitCodeTakenException(Throwable cause) {
        super("The unit code is already taken in this property.", cause);
    }
}
