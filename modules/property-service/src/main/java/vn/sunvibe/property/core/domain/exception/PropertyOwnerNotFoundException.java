package vn.sunvibe.property.core.domain.exception;

import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.io.Serial;

/** The owner an ADMIN named does not exist or is not active. The message never contains the id. */
public class PropertyOwnerNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PropertyOwnerNotFoundException() {
        super("Referenced owner was not found.");
    }
}
