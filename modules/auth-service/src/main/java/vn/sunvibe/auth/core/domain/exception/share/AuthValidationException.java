package vn.sunvibe.auth.core.domain.exception.share;

import lombok.Getter;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.shared.exception.ValidationException;

import java.io.Serial;

/**
 * An input value breaks a business rule (bad format, already in use, wrong
 * current password, ...). Carries the offending field so the error response
 * can point at it.
 */
@Getter
public class AuthValidationException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthFieldName field;

    public AuthValidationException(AuthFieldName field, String message) {
        super(field.getFieldName(), message);
        this.field = field;
    }

    /** Human-readable name of the offending field. */
    public String getDisplayName() {
        return field.getDisplayName();
    }

}
