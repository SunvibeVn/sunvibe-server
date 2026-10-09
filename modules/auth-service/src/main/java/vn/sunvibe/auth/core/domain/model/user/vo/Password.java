package vn.sunvibe.auth.core.domain.model.user.vo;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

@RequiredArgsConstructor
@Getter
public final class Password extends ValueObject<Password> {

    private final String hashedValue;

    // Build from new data
    public static Password create(String hashedValue) {
        if (hashedValue == null || hashedValue.isBlank()) {
            throw new AuthValidationException(AuthFieldName.PASSWORD, "Password must not be blank.");
        }
        return new Password(hashedValue);
    }

    // Build from database
    public static Password from(String hashedValue) {
        return new Password(hashedValue);
    }

    @Override
    protected boolean equalsCore(Password other) {
        return this.hashedValue.equals(other.hashedValue);
    }

    @Override
    protected int hashCodeCore() {
        return 0;
    }

}
