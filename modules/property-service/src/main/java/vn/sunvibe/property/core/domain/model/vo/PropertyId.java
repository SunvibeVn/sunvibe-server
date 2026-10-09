package vn.sunvibe.property.core.domain.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing PropertyId.
 * This is the unique identifier of a property in the system.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class PropertyId extends ValueObject<PropertyId> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String value;

    private PropertyId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PropertyId cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Generate a new PropertyId with a UUID.
     */
    public static PropertyId generate() {
        return new PropertyId(UUID.randomUUID().toString());
    }

    /**
     * Create a PropertyId from a String value.
     */
    public static PropertyId of(String value) {
        return new PropertyId(value);
    }

    @Override
    protected boolean equalsCore(PropertyId other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
