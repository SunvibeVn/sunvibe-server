package vn.sunvibe.property.core.domain.model.unit;

/**
 * Real-world availability of a unit (property-units BR-6). It says nothing about listings or bookings.
 * Stored as text in {@code units.availability_state}.
 */
public enum UnitAvailabilityState {
    /** Free. A new unit starts here (property-units BR-8). */
    AVAILABLE,
    /** Someone lives there: a tenant or the owner. */
    OCCUPIED,
    /** Being repaired. */
    MAINTENANCE,
    /** Held back: the owner keeps it, a legal issue, and so on. */
    BLOCKED
}
