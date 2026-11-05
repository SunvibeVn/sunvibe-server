package vn.sunvibe.property.core.domain.model.enums;

/**
 * How an intake property is let (property-create intake spec 4.8, section 8): as one whole unit, or in parts, one unit per
 * selected space. Stored as text in {@code properties.rental_layout}.
 */
public enum RentalLayout {
    WHOLE,
    PARTS
}
