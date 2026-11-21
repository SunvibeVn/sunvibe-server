package vn.sunvibe.listing.core.application.shared.external.property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port - property's viewer-less unit reads, as the review uses them (listing-publish E06-S4 spec E06S4-D11 to D13;
 * ADR-009). The caller has checked first that its own caller may see what it asks (ADR-012 decision 2): the reads check
 * nothing. Every type here is a {@code java.*} value or a record of this package.
 */
public interface UnitInventoryPort {

    /** The most unit ids one availability read takes, property's own bound. */
    int MAX_UNITS = 250;

    /**
     * The code and availability of each known unit, and whether any intake entry of it lists
     * {@code AVAILABILITY_DIFFERS}; an unknown unit is left out.
     *
     * @throws IllegalArgumentException more than {@link #MAX_UNITS} ids, before any read
     */
    List<UnitAvailabilityFacts> availability(Collection<String> unitIds);

    /**
     * The real location of the unit (E06S4-D12): only for a reviewer who may decide the revision now, checked by the
     * caller; the values are private and never logged. Empty for a unit property does not know.
     */
    Optional<UnitAddressFacts> address(String unitId);
}
