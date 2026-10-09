package vn.sunvibe.property.core.application.exception;

public class PropertyAlreadyExistsException extends RuntimeException {
    public PropertyAlreadyExistsException(String message) {
        super(message);
    }

    public PropertyAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
