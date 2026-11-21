package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAddressFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitInventoryPort;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cross-module composition (listing-publish E06-S4 spec E06S4-D13, ADR-009): answers listing-service's unit inventory port
 * with property-service's published, viewer-less {@link UnitIntakeUseCase} and {@link UnitLocationUseCase}, after
 * listing checked its caller. It forwards each call and copies the fields; the availability passes as property stores
 * it, unmapped. It joins the caller's transaction and logs nothing.
 */
@Component
@RequiredArgsConstructor
public class PropertyUnitInventoryAdapter implements UnitInventoryPort {

    /** property-service's reason of a unit whose intake marked another availability than the unit's (display only). */
    private static final String AVAILABILITY_DIFFERS = "AVAILABILITY_DIFFERS";

    private final UnitIntakeUseCase intakes;
    private final UnitLocationUseCase locations;

    @Override
    public List<UnitAvailabilityFacts> availability(Collection<String> unitIds) {
        if (unitIds.size() > MAX_UNITS) {
            throw new IllegalArgumentException("At most 250 units a call.");
        }
        return intakes.forUnits(unitIds).stream()
                .map(view -> new UnitAvailabilityFacts(view.unitId(), view.unitCodeOrNull(), view.availabilityState(),
                        differs(view)))
                .toList();
    }

    @Override
    public Optional<UnitAddressFacts> address(String unitId) {
        return locations.locate(List.of(unitId)).stream()
                .filter(view -> unitId.equals(view.unitId()))
                .findFirst()
                .map(view -> new UnitAddressFacts(view.unitId(), view.unitCodeOrNull(), view.locationLabelOrNull(),
                        view.streetOrNull(), view.wardNameOrNull()));
    }

    private static boolean differs(UnitIntakeView view) {
        return view.intakes().stream().anyMatch(entry -> entry.unitReasons().contains(AVAILABILITY_DIFFERS));
    }
}
