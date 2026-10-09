package vn.sunvibe.auth.core.domain.exception.share;

import lombok.Getter;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.common.exception.ValidationException;

import java.io.Serial;

@Getter
public class AuthValidationException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthFieldName field;

    public AuthValidationException(AuthFieldName field, String message) {
        super(field.getFieldName(), message);
        this.field = field;
    }

    public String getDisplayName() {
        return field.getDisplayName();
    }

}
