package vn.sunvibe.shared.exception;

import java.io.Serial;

public abstract class BusinessRuleViolationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
