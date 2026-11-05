package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a property is in dispute (property-create intake spec 4.11, property-create BR-19): a candidate in a live OPEN case other than
 * LINK_PLACE, or named, directly or as a COMPLETED winner's result, by a SAME_ASSET decision that holds its waiting intake.
 */
@Component
@RequiredArgsConstructor
public class IntakeDisputes {

    private final DuplicateCaseDomainRepository cases;
    private final InventoryIntakeDomainRepository intakes;
    private final IntakeEvaluator evaluator;

    public boolean disputed(String propertyId) {
        return casesNaming(propertyId).stream().map(evaluator::caseFacts).anyMatch(facts -> holds(facts, propertyId));
    }

    private List<DuplicateCase> casesNaming(String propertyId) {
        List<DuplicateCase> found = new ArrayList<>(cases.findNamingProperty(propertyId));
        for (InventoryIntake winner : intakes.findCompletedByResult(propertyId)) {
            cases.findForIntake(winner.getId()).stream()
                    .filter(item -> winner.getId().equals(item.getProceedingIntakeId())).forEach(found::add);
        }
        return found;
    }

    private static boolean holds(CaseFacts facts, String propertyId) {
        Reading reading = CasePolicy.read(facts);
        if (reading == Reading.OPEN) {
            return facts.row().getSignal() != CaseSignal.LINK_PLACE;
        }
        return reading == Reading.WAITING && propertyId.equals(CasePolicy.namedProperty(facts));
    }
}
