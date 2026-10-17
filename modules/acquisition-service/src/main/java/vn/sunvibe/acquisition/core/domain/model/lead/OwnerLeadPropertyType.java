package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * The kind of property the owner describes on the intake form. Only a hint for staff; no property is created.
 */
public enum OwnerLeadPropertyType {
    /** Single-room apartment where living and sleeping share one space. */
    STUDIO,
    /** Apartment with one bedroom. */
    ONE_BEDROOM,
    /** Apartment with two bedrooms. */
    TWO_BEDROOM,
    /** Apartment with three bedrooms. */
    THREE_BEDROOM,
    /** Apartment with four bedrooms. */
    FOUR_BEDROOM,
    /** Apartment spread over two connected floors. */
    DUPLEX,
    /** Apartment on the top floor of a building. */
    PENTHOUSE,
    /** Narrow multi-storey house in a row. */
    TOWNHOUSE,
    /** Land without a finished building. */
    LAND,
    /** Anything not covered by the other types. */
    OTHER
}
