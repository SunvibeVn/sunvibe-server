package vn.sunvibe.property.core.domain.model.unit;

import com.github.f4b6a3.ulid.UlidCreator;
import vn.sunvibe.property.core.domain.model.vo.ValueObject;

/**
 * Value Object representing UnitId: a ULID of 26 characters (ADR-006), stored as {@code units.id}.
 */
public final class UnitId extends ValueObject<UnitId> {

    private final String value;

    private UnitId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UnitId cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Generate a new UnitId with a ULID.
     */
    public static UnitId generate() {
        return new UnitId(UlidCreator.getUlid().toString());
    }

    /**
     * Create a UnitId from a String value.
     */
    public static UnitId of(String value) {
        return new UnitId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected boolean equalsCore(UnitId other) {
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
