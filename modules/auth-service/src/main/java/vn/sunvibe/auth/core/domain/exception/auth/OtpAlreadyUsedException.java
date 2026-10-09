package vn.sunvibe.auth.core.domain.exception.auth;

import vn.sunvibe.common.exception.DomainException;

import java.io.Serial;

public class OtpAlreadyUsedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OtpAlreadyUsedException(String message) {
        super(message);
    }
}
