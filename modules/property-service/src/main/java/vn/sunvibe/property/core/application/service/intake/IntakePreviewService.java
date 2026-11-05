package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakePreviewUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakePreviewView;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;

import java.time.Instant;

/** Use Case Implementation - the preview (property-create intake spec 7.1). Not read-only: it writes the access log row. */
@Service
@RequiredArgsConstructor
public class IntakePreviewService implements IntakePreviewUseCase {

    private static final String PREVIEW_READ = "PREVIEW";

    private final IntakeLeadPort leads;
    private final IntakeEvaluator evaluator;
    private final IntakeCommandSupport support;
    private final IntakePreviewBuilder builder;
    private final IntakeTrailDomainRepository trail;

    @Override
    @Transactional
    public IntakePreviewView preview(String leadId, IntakeActor actor) {
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = leads.findVisible(leadId, actor.viewer())
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
        support.requireAssignee(lead, actor);
        IntakeState state = evaluator.read(lead, now);
        support.requireCheckedRow(state);
        support.requireGate(state);
        InventoryIntake row = state.currentOrNull();
        if (row.getTarget() == null || row.getAvailability().isEmpty()) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
        IntakeSource source = state.source().orElseThrow(() -> new IntakeRefusedException(IntakeError.INTAKE_STATE));
        IntakePreviewBuilder.requireStorable(source);
        trail.recordAccess(actor.userId(), leadId, row.getId(), null, PREVIEW_READ, now);
        return builder.view(state, source, actor);
    }
}
