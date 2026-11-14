package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.InventoryIntakeReadinessUseCase;

import java.time.Instant;
import java.util.List;

/** Use Case Implementation - readiness (property-create intake spec 4.11). Nothing here is stored, so a change shows at once. */
@Service
@RequiredArgsConstructor
public class InventoryIntakeReadinessService implements InventoryIntakeReadinessUseCase {

    private final IntakeLeadPort leads;
    private final IntakeEvaluator evaluator;
    private final IntakeReadiness readiness;

    @Override
    @Transactional(readOnly = true)
    public IntakeBlockView.Readiness readiness(String leadId) {
        IntakeLead lead = leads.systemFacts(List.of(leadId)).get(leadId);
        if (lead == null) {
            throw new IntakeRefusedException(IntakeError.NOT_FOUND);
        }
        Instant now = IntakeCommandSupport.now();
        return readiness.view(evaluator.read(lead, now), now);
    }
}
