package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeHistoryEntry;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeRestorationUseCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessReason;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;

import java.time.Instant;

/**
 * Use Case Implementation - restoration (property-create intake spec 4.2, 4.11, S-9). It needs a live proof of the result's owner
 * with its check DONE, the gate ready and no dispute, and it never picks up an old proof: only the proof in force counts.
 */
@Service
@RequiredArgsConstructor
public class IntakeRestorationService implements IntakeRestorationUseCase {

    private final IntakeCommandSupport support;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final IntakeReadiness readiness;
    private final IntakeDisputes disputes;
    private final IntakeLeadPort leads;
    private final InventoryIntakeDomainRepository intakes;
    private final InventoryReadRepository inventory;

    @Override
    @Transactional
    public IntakeBlockView restore(String leadId, IntakeActor actor, Long expectedVersion) {
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        support.requireAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        boolean unconfirmedResult = state.effectiveOrNull() == IntakeStatus.COMPLETED
                && readiness.result(state).reasons().contains(ReadinessReason.OWNER_NOT_CONFIRMED);
        if (!unconfirmedResult) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
        support.requireGate(state);
        InventoryIntake row = state.currentOrNull();
        requireProofOfTheOwner(state, row);
        support.requireVersion(state, expectedVersion);
        if (disputes.disputed(row.getResultPropertyId())) {
            throw new IntakeRefusedException(IntakeError.INTAKE_DISPUTED);
        }

        support.settle(state, now);
        row.restore(state.proofOrNull().getId(), now);
        intakes.update(row);
        support.event(row.getId(), actor, IntakeEventAction.RESTORATION, now);
        leads.recordActivity(leadId, new IntakeHistoryEntry(IntakeHistoryEntry.Type.INVENTORY_INTAKE_RESTORED, actor.userId(),
                row.getId(), row.getIntakeNo(), now));
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    private void requireProofOfTheOwner(IntakeState state, InventoryIntake row) {
        boolean sameOwner = state.checkDone() && inventory.property(row.getResultPropertyId())
                .map(InventoryReadRepository.PropertyFacts::ownerId).filter(state.proofOrNull().getUserId()::equals).isPresent();
        if (!sameOwner) {
            throw new IntakeRefusedException(IntakeError.OWNER_PROOF_REQUIRED);
        }
    }
}
