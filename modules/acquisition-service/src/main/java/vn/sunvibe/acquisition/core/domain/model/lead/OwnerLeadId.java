package vn.sunvibe.acquisition.core.domain.model.lead;

import com.github.f4b6a3.ulid.UlidCreator;
import vn.sunvibe.acquisition.core.domain.model.ValueObject;

/**
 * Value Object representing OwnerLeadId: a ULID of 26 characters (ADR-006), stored as {@code owner_leads.id}.
 */
public final class OwnerLeadId extends ValueObject<OwnerLeadId> {

    private final String value;

    private OwnerLeadId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OwnerLeadId cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Generate a new OwnerLeadId with a ULID.
     */
    public static OwnerLeadId generate() {
        return new OwnerLeadId(UlidCreator.getUlid().toString());
    }

    /**
     * Create an OwnerLeadId from a String value.
     */
    public static OwnerLeadId of(String value) {
        return new OwnerLeadId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected boolean equalsCore(OwnerLeadId other) {
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
