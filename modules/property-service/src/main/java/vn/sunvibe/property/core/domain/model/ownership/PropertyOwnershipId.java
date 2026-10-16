package vn.sunvibe.property.core.domain.model.ownership;

import com.github.f4b6a3.ulid.UlidCreator;
import vn.sunvibe.property.core.domain.model.vo.ValueObject;

/**
 * Value Object representing PropertyOwnershipId: a ULID of 26 characters (ADR-006),
 * stored as {@code property_ownerships.id}.
 */
public final class PropertyOwnershipId extends ValueObject<PropertyOwnershipId> {

    private final String value;

    private PropertyOwnershipId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PropertyOwnershipId cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Generate a new PropertyOwnershipId with a ULID.
     */
    public static PropertyOwnershipId generate() {
        return new PropertyOwnershipId(UlidCreator.getUlid().toString());
    }

    /**
     * Create a PropertyOwnershipId from a String value.
     */
    public static PropertyOwnershipId of(String value) {
        return new PropertyOwnershipId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected boolean equalsCore(PropertyOwnershipId other) {
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
