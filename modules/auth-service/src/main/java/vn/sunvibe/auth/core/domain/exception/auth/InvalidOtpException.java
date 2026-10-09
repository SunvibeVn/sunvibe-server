package vn.sunvibe.auth.core.domain.exception.auth;


import vn.sunvibe.common.exception.DomainException;

import java.io.Serial;

public class InvalidOtpException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidOtpException(String message) {
        super(message);
    }
}
