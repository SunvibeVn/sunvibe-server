package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationPort;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;

import java.util.Collection;
import java.util.List;

/**
 * Cross-module composition (brokerage-agreement E05-S5 spec S5-D2, ADR-009): answers brokerage-service's location
 * question with property-service's published {@link UnitLocationUseCase}. It only copies the values, and joins the
 * caller's transaction; brokerage calls it only after its own ownership check.
 */
@Component
@RequiredArgsConstructor
public class PropertyUnitLocationAdapter implements UnitLocationPort {

    private final UnitLocationUseCase locations;

    @Override
    public List<UnitLocationFacts> locate(Collection<String> unitIds) {
        return locations.locate(unitIds).stream()
                .map(view -> new UnitLocationFacts(view.unitId(), view.unitCodeOrNull(), view.locationLabelOrNull(),
                        view.streetOrNull(), view.wardNameOrNull()))
                .toList();
    }
}
