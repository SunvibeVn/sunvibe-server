package vn.sunvibe.auth.core.domain.exception.auth;


import vn.sunvibe.common.exception.BusinessRuleViolationException;

import java.io.Serial;

public class TokenExpiredException extends BusinessRuleViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TokenExpiredException(String message) {
        super(message);
    }
}
