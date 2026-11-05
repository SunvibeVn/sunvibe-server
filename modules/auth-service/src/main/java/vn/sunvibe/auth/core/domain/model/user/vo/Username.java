package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;

/**
 * The stored phone of an account, in the form people read it (e.g. 0912345678). Input goes through
 * {@link PhoneNumber}; a value stored by an older release may not pass that policy, so this class does not check.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Username extends ValueObject<Username> {

    private final String value;

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
