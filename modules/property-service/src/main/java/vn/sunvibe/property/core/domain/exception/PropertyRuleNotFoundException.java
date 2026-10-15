package vn.sunvibe.property.core.domain.exception;

import vn.sunvibe.common.exception.ResourceNotFoundException;

import java.io.Serial;

public class PropertyRuleNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PropertyRuleNotFoundException() {
        super("Rule reference is invalid.");
    }
}
