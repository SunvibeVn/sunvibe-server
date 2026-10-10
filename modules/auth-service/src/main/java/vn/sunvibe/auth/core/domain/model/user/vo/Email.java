package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An e-mail address, always stored lower-case so lookups are case-insensitive.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Email extends ValueObject<Email> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private final String value;

    /**
     * Builds from user input.
     *
     * @throws AuthValidationException if the format is invalid
     */
    public static Email create(String value) {
        if (value == null || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new AuthValidationException(AuthFieldName.EMAIL, "Invalid email format.");
        }
        return new Email(value.toLowerCase(Locale.ROOT));
    }

    /** Rebuilds from already-validated, persisted data. */
    public static Email from(String value) {
        return new Email(value.toLowerCase(Locale.ROOT));
    }

    @Override
    protected boolean equalsCore(Email other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }

}
