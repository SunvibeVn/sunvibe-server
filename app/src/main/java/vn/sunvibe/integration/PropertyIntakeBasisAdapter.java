package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisPort;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeView;

import java.util.Optional;

/**
 * Cross-module composition (E05-S1 spec 5.5, ADR-009): answers brokerage-service's basis question with property-service's
 * published {@link CompletedIntakeUseCase}. It only copies the values, and joins the caller's transaction; brokerage
 * calls it only after its own visibility check.
 */
@Component
@RequiredArgsConstructor
public class PropertyIntakeBasisAdapter implements IntakeBasisPort {

    private final CompletedIntakeUseCase completedIntakes;

    @Override
    public Optional<IntakeBasisFacts> latestBasis(String leadId) {
        return completedIntakes.latest(leadId).map(PropertyIntakeBasisAdapter::basis);
    }

    private static IntakeBasisFacts basis(CompletedIntakeView view) {
        return new IntakeBasisFacts(view.intakeId(), view.propertyId(), view.readiness().state(),
                view.readiness().units().stream()
                        .map(unit -> new IntakeBasisFacts.Unit(unit.unitId(), unit.unitCode(), unit.reasons()))
                        .toList(),
                view.ownerIdOrNull(),
                view.holdings().stream()
                        .map(holding -> new IntakeBasisFacts.Holding(holding.userId(), holding.relationType(),
                                holding.verificationState()))
                        .toList());
    }
}
