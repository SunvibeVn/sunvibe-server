package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-module composition (brokerage-agreement E05-S3 spec S3-D17, ADR-009): answers brokerage-service's origin
 * question with property-service's published {@link IntakeOriginUseCase}. It only copies the values, and joins the
 * caller's transaction; brokerage calls it only after its own visibility check.
 */
@Component
@RequiredArgsConstructor
public class PropertyIntakeOriginAdapter implements IntakeOriginPort {

    private final IntakeOriginUseCase origins;

    @Override
    public Optional<IntakeOriginFacts> originOf(String intakeId) {
        return origins.originOf(intakeId).map(PropertyIntakeOriginAdapter::facts);
    }

    private static IntakeOriginFacts facts(IntakeOriginView view) {
        // a unit mapped twice keeps its first space: the mapping rows name one space per unit (java-core.md)
        Map<String, String> spaceIdByUnitId = new LinkedHashMap<>();
        view.units().forEach(unit -> spaceIdByUnitId.putIfAbsent(unit.unitId(), unit.spaceId()));
        return new IntakeOriginFacts(view.leadId(), view.intentCycle(), view.sourceRevisionId(), spaceIdByUnitId);
    }
}
