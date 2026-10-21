package vn.sunvibe.property.core.domain.exception;

import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.io.Serial;

public class PropertyNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PropertyNotFoundException(String message) {
        super(message);
    }

}
