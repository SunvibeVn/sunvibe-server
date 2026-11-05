package vn.sunvibe.property.core.domain.exception;

/** A command outside the states of property-create intake spec 4.2. The message names no value of the intake. */
public class IntakeStateException extends RuntimeException {

    public IntakeStateException(String command) {
        super("The intake is not in a state for: " + command);
    }
}
