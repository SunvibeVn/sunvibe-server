package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * The kind of property the owner describes on the intake form: one of the six property types Sunvibe takes, in the
 * order of the form (acquisition-owner-intake BR-3). There is no "other" value; boarding houses, land and commercial
 * space are not Sunvibe types. Only a hint for staff; no property is created.
 */
public enum OwnerLeadPropertyType {
    /** A whole house rented as one (nhà nguyên căn). */
    HOUSE,
    /** A building of one owner with apartments or rooms inside (tòa nhà / tòa căn hộ). */
    WHOLE_BUILDING,
    /** A villa. */
    VILLA,
    /** A homestay, rented per room or whole. */
    HOMESTAY,
    /** An apartment in a hotel building, a condotel (căn hộ khách sạn). */
    CONDOTEL,
    /** An apartment in a condominium building (căn hộ chung cư). */
    APARTMENT
}
