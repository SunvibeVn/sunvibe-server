package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

/**
 * A password hash. The raw password never reaches the domain: it is hashed by
 * the application layer before this object is built.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Password extends ValueObject<Password> {

    private final String hashedValue;

    /**
     * Wraps a freshly computed hash.
     *
     * @throws AuthValidationException if the hash is blank
     */
    public static Password create(String hashedValue) {
        if (hashedValue == null || hashedValue.isBlank()) {
            throw new AuthValidationException(AuthFieldName.PASSWORD, "Password must not be blank.");
        }
        return new Password(hashedValue);
    }

    /** Rebuilds from a persisted hash. */
    public static Password from(String hashedValue) {
        return new Password(hashedValue);
    }

    @Override
    protected boolean equalsCore(Password other) {
        return this.hashedValue.equals(other.hashedValue);
    }

    @Override
    protected int hashCodeCore() {
        return hashedValue.hashCode();
    }

}
