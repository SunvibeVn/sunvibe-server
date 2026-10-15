package vn.sunvibe.auth.core.domain.exception.user;

import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.io.Serial;

/**
 * No (non-deleted) account matches the lookup. Answered with 404 by the
 * presentation layer.
 */
public class UserNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UserNotFoundException(String message) {
        super(message);
    }

}
