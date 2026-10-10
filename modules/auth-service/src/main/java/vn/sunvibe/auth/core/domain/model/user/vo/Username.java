package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.regex.Pattern;

/**
 * The sign-in name of an account: its phone number, 10 to 12 digits
 * (e.g. 0912345678).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Username extends ValueObject<Username> {

    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^[0-9]{10,12}$");

    private final String value;

    /**
     * Builds from user input.
     *
     * @throws AuthValidationException if the value is not a 10-12 digit phone number
     */
    public static Username create(String value) {
        if (value == null || !PHONE_NUMBER_PATTERN.matcher(value).matches()) {
            throw new AuthValidationException(AuthFieldName.USER_NAME, "Phone number must be between 10-12 digits.");
        }
        return new Username(value);
    }

    /** Rebuilds from persisted data. */
    public static Username from(String value) {
        return new Username(value);
    }

    @Override
    protected boolean equalsCore(Username other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }

}
