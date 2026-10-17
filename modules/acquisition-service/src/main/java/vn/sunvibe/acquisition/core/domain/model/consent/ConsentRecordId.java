package vn.sunvibe.acquisition.core.domain.model.consent;

import com.github.f4b6a3.ulid.UlidCreator;
import vn.sunvibe.acquisition.core.domain.model.ValueObject;

/**
 * Value Object representing ConsentRecordId: a ULID of 26 characters (ADR-006), stored as {@code consent_records.id}.
 */
public final class ConsentRecordId extends ValueObject<ConsentRecordId> {

    private final String value;

    private ConsentRecordId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ConsentRecordId cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Generate a new ConsentRecordId with a ULID.
     */
    public static ConsentRecordId generate() {
        return new ConsentRecordId(UlidCreator.getUlid().toString());
    }

    /**
     * Create a ConsentRecordId from a String value.
     */
    public static ConsentRecordId of(String value) {
        return new ConsentRecordId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected boolean equalsCore(ConsentRecordId other) {
        return other != null && value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
