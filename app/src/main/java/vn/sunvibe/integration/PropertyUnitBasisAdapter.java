package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisPort;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;

import java.util.Collection;
import java.util.List;

/**
 * Cross-module composition (E05-S6 spec S6-D17, ADR-009; ADR-012 decision 2, reads 1 and 1b): answers brokerage-service's
 * set reads of the units' bases and the properties' owners with property-service's published {@link UnitIntakeUseCase}
 * and {@link PropertyOwnershipUseCase}. It only translates records, dropping the availability and the source revision
 * that brokerage does not compare, and joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class PropertyUnitBasisAdapter implements UnitBasisPort {

    private final UnitIntakeUseCase unitIntakes;
    private final PropertyOwnershipUseCase ownerships;

    @Override
    public List<UnitBasisFacts> forUnits(Collection<String> unitIds) {
        return unitIntakes.forUnits(unitIds).stream()
                .map(unit -> new UnitBasisFacts(unit.unitId(), unit.propertyId(), unit.intakes().stream()
                        .map(entry -> new UnitBasisFacts.Entry(entry.intakeId(), entry.state(), entry.unitReasons()))
                        .toList()))
                .toList();
    }

    @Override
    public List<PropertyOwnerFacts> ownersOf(Collection<String> propertyIds) {
        return ownerships.ownershipOf(propertyIds).stream()
                .map(property -> new PropertyOwnerFacts(property.propertyId(), property.ownerIdOrNull(),
                        property.holdings().stream()
                                .map(holding -> new IntakeBasisFacts.Holding(holding.userId(), holding.relationType(),
                                        holding.verificationState()))
                                .toList()))
                .toList();
    }
}
