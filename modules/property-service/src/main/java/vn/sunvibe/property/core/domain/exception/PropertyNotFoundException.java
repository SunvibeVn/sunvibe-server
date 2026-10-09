package vn.sunvibe.property.core.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import vn.sunvibe.common.exception.ResourceNotFoundException;

import java.io.Serial;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PropertyNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PropertyNotFoundException(String message) {
        super(message);
    }

}
