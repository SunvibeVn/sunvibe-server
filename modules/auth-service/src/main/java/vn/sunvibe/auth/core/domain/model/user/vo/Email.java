package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.regex.Pattern;

@RequiredArgsConstructor
@Getter
@Setter
public final class Email extends ValueObject<Email> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private final String value;

    // Build from new data
    public static Email create(String value) {
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new AuthValidationException(AuthFieldName.EMAIL, "Invalid email format.");
        }
        return new Email(value.toLowerCase());
    }

    // Build from database
    public static Email from(String value) {
        return new Email(value.toLowerCase());
    }

    @Override
    protected boolean equalsCore(Email other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return 0;
    }

}
