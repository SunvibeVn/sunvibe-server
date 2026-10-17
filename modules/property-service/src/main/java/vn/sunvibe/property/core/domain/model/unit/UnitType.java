package vn.sunvibe.property.core.domain.model.unit;

import vn.sunvibe.property.core.domain.model.enums.PropertyType;

/**
 * Kind of unit (property-units BR-5). Same values as {@link PropertyType}, mapped by name, so the later unit type list
 * (property-units Q-14) does not change the property type (property-units spec A-6). Stored as text in {@code units.unit_type}.
 */
public enum UnitType {
    STUDIO,
    ONE_BEDROOM,
    TWO_BEDROOM,
    THREE_BEDROOM,
    FOUR_BEDROOM,
    DUPLEX,
    PENTHOUSE;

    /**
     * The unit type with the same name as the property type.
     */
    public static UnitType from(PropertyType type) {
        if (type == null) {
            throw new IllegalArgumentException("Property type is required.");
        }
        return valueOf(type.name());
    }
}
