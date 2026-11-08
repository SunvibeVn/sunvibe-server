package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeReadUseCase;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;

import java.time.Instant;

/** Use Case Implementation - the intake block. Not read-only: it writes the access log row (property-create intake spec 4.12). */
@Service
@RequiredArgsConstructor
public class InventoryIntakeReadService implements IntakeReadUseCase {

    private final IntakeLeadPort leads;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final IntakeTrailDomainRepository trail;

    @Override
    @Transactional
    public IntakeBlockView block(String leadId, IntakeActor actor) {
        IntakeLead lead = leads.findVisible(leadId, actor.viewer())
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
        Instant now = IntakeCommandSupport.now();
        IntakeState state = evaluator.read(lead, now);
        trail.recordAccess(actor.userId(), leadId, state.currentOrNull() == null ? null : state.currentOrNull().getId(), null,
                "BLOCK_READ", now);
        return assembler.block(state, actor, now);
    }
}
