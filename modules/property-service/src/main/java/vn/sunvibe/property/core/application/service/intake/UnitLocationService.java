package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationView;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.UnitFacts;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - the location of units (brokerage-agreement E05-S5 spec S5-D2). Two statements: the units,
 * then their properties. No viewer: the consumer has already checked that its caller owns the agreements of these units.
 * Nothing is written.
 */
@Service
@RequiredArgsConstructor
public class UnitLocationService implements UnitLocationUseCase {

    private final InventoryReadRepository inventory;

    @Override
    @Transactional(readOnly = true)
    public List<UnitLocationView> locate(Collection<String> unitIds) {
        if (unitIds.size() > MAX_UNITS) {
            throw new IllegalArgumentException("At most " + MAX_UNITS + " units are located in one call.");
        }
        List<UnitFacts> units = inventory.units(unitIds);
        if (units.isEmpty()) {
            return List.of();
        }
        Map<String, PropertyFacts> properties = inventory.properties(units.stream().map(UnitFacts::propertyId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(PropertyFacts::id, Function.identity()));
        return units.stream().map(unit -> {
            PropertyFacts property = properties.get(unit.propertyId());
            return new UnitLocationView(unit.id(), unit.code(), unit.locationLabelOrNull(),
                    property == null ? null : property.street(), property == null ? null : property.wardName());
        }).toList();
    }
}
