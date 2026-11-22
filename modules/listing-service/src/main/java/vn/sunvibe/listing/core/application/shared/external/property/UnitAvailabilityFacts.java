package vn.sunvibe.listing.core.application.shared.external.property;

/**
 * One unit as the review and the put live read it from property (listing-publish E06-S4 spec E06S4-D11, E06S4-D13;
 * E06-S6 spec E06S6-D4): its code and availability as stored (listing maps the availability), and whether any intake
 * entry of the unit lists {@code AVAILABILITY_DIFFERS}, which fails display condition (d) of listing BR-4.
 */
public record UnitAvailabilityFacts(String unitId, String unitCodeOrNull, String availabilityOrNull,
                                    boolean availabilityDiffers) {
}
