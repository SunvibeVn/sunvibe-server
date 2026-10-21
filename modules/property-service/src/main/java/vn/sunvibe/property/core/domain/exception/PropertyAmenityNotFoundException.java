package vn.sunvibe.property.core.domain.exception;

import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.io.Serial;

public class PropertyAmenityNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PropertyAmenityNotFoundException(String message) {
        super(message);
    }

}
