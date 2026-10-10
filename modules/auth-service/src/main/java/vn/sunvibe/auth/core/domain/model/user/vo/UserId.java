package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

/**
 * Immutable identifier of a user account (a 26-character ULID).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class UserId extends ValueObject<UserId> {

    private final String value;

    /**
     * Wraps a newly generated id.
     *
     * @throws AuthValidationException if the id is blank
     */
    public static UserId create(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthValidationException(AuthFieldName.USER_ID, "User id is required.");
        }
        return new UserId(value);
    }

    /** Rebuilds from persisted data. */
    public static UserId from(String value) {
        return new UserId(value);
    }

    @Override
    protected boolean equalsCore(UserId other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }
}
