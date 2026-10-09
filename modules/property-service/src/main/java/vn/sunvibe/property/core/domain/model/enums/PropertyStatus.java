package vn.sunvibe.property.core.domain.model.enums;

public enum PropertyStatus {

    AVAILABLE,      // Ready for rent/sale
    UNAVAILABLE,    // Not available (suspended, locked)
    RENTED,         // Currently rented
    SOLD,           // Already sold
    UNDER_MAINTENANCE, // Under maintenance, no transactions allowed
    PENDING_APPROVAL,  // Awaiting approval (e.g. admin or owner confirmation)
    DRAFT           // Newly created, not yet public

}
