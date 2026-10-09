package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.regex.Pattern;

@RequiredArgsConstructor
@Getter
public final class Username extends ValueObject<Username> {

    private static final String PHONE_NUMBER_PATTERN = "^[0-9]{10,12}$";
    private static final Pattern pattern = Pattern.compile(PHONE_NUMBER_PATTERN);

    private final String value;

    // Build from new data
    public static Username create(String value) {
        validateUsername(value);
        return new Username(value);
    }

    // Build from database
    public static Username from(String value) {
        return new Username(value);
    }

    private static void validateUsername(String username) {
        if (!pattern.matcher(username).matches()) {
            throw new AuthValidationException(AuthFieldName.USER_NAME, "Phone number must be between 10-12 digits."
            );
        }
    }

    @Override
    protected boolean equalsCore(Username other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return 0;
    }

}