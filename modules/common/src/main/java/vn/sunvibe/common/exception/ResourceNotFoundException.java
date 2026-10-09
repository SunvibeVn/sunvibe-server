package vn.sunvibe.common.exception;

import java.io.Serial;

public abstract class ResourceNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }

}
