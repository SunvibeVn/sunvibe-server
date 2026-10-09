package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.regex.Pattern;

/**
 * A Vietnamese national ID card number: either the legacy 9-digit CMND
 * or the current 12-digit CCCD format. The plaintext value never leaves
 * this object's constructor validation — persistence-layer encryption
 * (see {@code EncryptedStringAttributeConverter}) is an infrastructure
 * concern this value object has no knowledge of.
 */
@Getter
public final class IdCardNumber extends ValueObject<IdCardNumber> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{9}|\\d{12})$");

    private final String value;

    private IdCardNumber(String value) {
        this.value = value;
    }

    /** Build from new, user-supplied data — validates the format. */
    public static IdCardNumber create(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new AuthValidationException(AuthFieldName.ID_CARD_NUMBER,
                    "ID card number must be a 9-digit CMND or 12-digit CCCD.");
        }
        return new IdCardNumber(value);
    }

    /** Build from already-validated, persisted data — no re-validation. */
    public static IdCardNumber from(String value) {
        return new IdCardNumber(value);
    }

    @Override
    protected boolean equalsCore(IdCardNumber other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }
}
