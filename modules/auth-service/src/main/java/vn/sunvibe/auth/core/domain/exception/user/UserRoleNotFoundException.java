package vn.sunvibe.auth.core.domain.exception.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import vn.sunvibe.common.exception.ResourceNotFoundException;

import java.io.Serial;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserRoleNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UserRoleNotFoundException(String message) {
        super(message);
    }

}
