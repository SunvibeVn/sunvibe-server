package vn.sunvibe.property.core.domain.exception;

import vn.sunvibe.shared.exception.BusinessRuleViolationException;
import vn.sunvibe.property.core.domain.model.aggregate.Property;

import java.io.Serial;

/**
 * A property value breaks a rule. {@link #getMessage()} is the plain message the API returns on
 * {@link #getFieldName()}, without the field's display name the superclass message starts with.
 */
public class PropertyValidationException extends BusinessRuleViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Property.FieldName field;
    private final String message;

    public PropertyValidationException(Property.FieldName field, String message) {
        super(buildErrorMessage(field, message));
        this.field = field;
        this.message = message;
    }

    private static String buildErrorMessage(Property.FieldName field, String message) {
        return String.format("[%s] %s", field.getDisplayName(), message);
    }

    public Property.FieldName getField() {
        return field;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getFieldName() {
        return field.getFieldName();
    }

}
