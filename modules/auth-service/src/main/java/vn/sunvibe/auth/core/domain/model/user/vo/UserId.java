package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;

@RequiredArgsConstructor
@Getter
public class UserId extends ValueObject<UserId> {

    private final String value;

    // Build from new data
    public static UserId create(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthValidationException(AuthFieldName.USER_ID, "User id is required.");
        }
        return new UserId(value);
    }

    // Build from database
    public static UserId from(String value) {
        return new UserId(value);
    }

    @Override
    protected boolean equalsCore(UserId other) {
        return this.value.equals(other.value);
    }


    @Override
    protected int hashCodeCore() {
        return 0;
    }
}
