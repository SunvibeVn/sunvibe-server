package vn.sunvibe.listing.core.domain.model.listing;

/** The unit an amount fee is charged per, E05's closed list (brokerage-agreement BR-8; E06-S2 spec E06S2-D6). */
public enum ChargeUnit {
    PER_MONTH,
    PER_KWH,
    PER_M3,
    PER_PERSON_PER_MONTH,
    PER_VEHICLE_PER_MONTH,
    ONE_OFF
}
