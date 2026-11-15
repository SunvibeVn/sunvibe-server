package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeView;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use Case Implementation - the latest COMPLETED intake of a lead with its readiness and the ownership of its result
 * property today (E05-S1 spec 5.4). The lead is read with no viewer, as the readiness is: the consumer has already
 * checked that its caller may see the lead. Nothing is written.
 */
@Service
@RequiredArgsConstructor
public class CompletedIntakeService implements CompletedIntakeUseCase {

    private final IntakeLeadPort leads;
    private final IntakeEvaluator evaluator;
    private final IntakeReadiness readiness;
    private final InventoryReadRepository inventory;

    @Override
    @Transactional(readOnly = true)
    public Optional<CompletedIntakeView> latest(String leadId) {
        IntakeLead lead = leads.systemFacts(List.of(leadId)).get(leadId);
        if (lead == null) {
            return Optional.empty();
        }
        Instant now = IntakeCommandSupport.now();
        IntakeState state = evaluator.read(lead, now);
        InventoryIntake row = state.latestCompletedOrNull();
        if (row == null) {
            return Optional.empty();
        }
        String propertyId = row.getResultPropertyId();
        String ownerIdOrNull = inventory.property(propertyId).map(InventoryReadRepository.PropertyFacts::ownerId)
                .orElse(null);
        List<CompletedIntakeView.OwnershipHolding> holdings = inventory.holdingsOn(propertyId,
                        IntakeCommandSupport.today(now)).stream()
                .map(holding -> new CompletedIntakeView.OwnershipHolding(holding.userId(), holding.relationType(),
                        holding.verificationState()))
                .toList();
        return Optional.of(new CompletedIntakeView(row.getId(), propertyId, readiness.view(state, now), ownerIdOrNull,
                holdings));
    }
}
