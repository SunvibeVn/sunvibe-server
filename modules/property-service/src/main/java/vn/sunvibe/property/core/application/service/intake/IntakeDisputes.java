package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a property is in dispute (property-create intake spec 4.11, property-create BR-19): a candidate in a live OPEN case other than
 * LINK_PLACE, or named, directly or as a COMPLETED winner's result, by a SAME_ASSET decision that holds its waiting intake.
 * The readiness of a result also reads the stored decisions that stop the whole asset (brokerage-agreement BR-3 (a) and
 * (c), SV-215).
 */
@Component
@RequiredArgsConstructor
public class IntakeDisputes {

    private final DuplicateCaseDomainRepository cases;
    private final InventoryIntakeDomainRepository intakes;
    private final OwnerProofDomainRepository proofs;
    private final IntakeEvaluator evaluator;

    public boolean disputed(String propertyId) {
        return casesNaming(propertyId).stream().map(evaluator::caseFacts).anyMatch(facts -> holds(facts, propertyId));
    }

    /**
     * brokerage-agreement BR-3 (c), E-15 (SV-215): a SAME_ASSET decision that names the property, directly or as a COMPLETED
     * winner's result, showed another owner: its waiting intake has a checked proof of an account other than the
     * property's owner that was in force when the manager ruled or was confirmed after ({@link CasePolicy#showsAnotherOwner}).
     * An OPEN case, the property owner's own proofs and a proof that died before the decision do not count; a proof that
     * counted keeps counting after it dies, until a separate flow exists. Read from the stored cases and proofs.
     */
    public boolean ownerDisputed(String propertyId, String ownerIdOrNull) {
        for (DuplicateCase row : casesNaming(propertyId)) {
            if (row.getStatus() != CaseStatus.DECIDED || row.getOutcome() != CaseOutcome.SAME_ASSET) {
                continue;
            }
            List<OwnerProof> ofWaitingLead = intakes.findById(CasePolicy.waitingIntake(row))
                    .map(waiting -> proofs.findByLeadId(waiting.getLeadId())).orElse(List.of());
            if (CasePolicy.showsAnotherOwner(row, ofWaitingLead, ownerIdOrNull)) {
                return true;
            }
        }
        return false;
    }

    /**
     * brokerage-agreement BR-3 (a), property-create D5-12 and BR-20 (SV-215): a manager ruled that an intake of another lead
     * is a different asset from the property, and that lead has an earlier COMPLETED result on the property, so the result
     * stands on an asset only reconciliation can put right. Read from the stored cases and rows, whatever that lead's status.
     */
    public boolean resultRuledOut(String propertyId, String exceptLeadId) {
        List<DuplicateCase> rulings = cases.findNamingProperty(propertyId).stream()
                .filter(row -> row.getStatus() == CaseStatus.DECIDED && row.getOutcome() == CaseOutcome.DIFFERENT_ASSET)
                .toList();
        if (rulings.isEmpty()) {
            return false;
        }
        List<InventoryIntake> onProperty = intakes.findCompletedByResult(propertyId);
        for (DuplicateCase row : rulings) {
            Optional<String> lead = intakes.findById(row.getIntakeId()).map(InventoryIntake::getLeadId)
                    .filter(leadId -> !leadId.equals(exceptLeadId));
            if (lead.isPresent() && onProperty.stream().anyMatch(result -> result.getLeadId().equals(lead.get())
                    && !result.getId().equals(row.getIntakeId()))) {
                return true;
            }
        }
        return false;
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
