package vn.sunvibe.property.core.domain.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing PropertyCode.
 * Example: DA-APT-001, DA-APT-002 (property identifier).
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class PropertyCode extends ValueObject<PropertyCode> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String value;

    private PropertyCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PropertyCode cannot be null or empty");
        }
        if (!value.matches("^[A-Z]{2}-[A-Z]{3}-\\d{3,}$")) {
            throw new IllegalArgumentException(
                    "PropertyCode must follow the format: DA-APT-001"
            );
        }
        this.value = value.toUpperCase();
    }

    public static PropertyCode of(String value) {
        return new PropertyCode(value);
    }

    @Override
    protected boolean equalsCore(PropertyCode other) {
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