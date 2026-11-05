package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSourcePort;
import vn.sunvibe.property.core.domain.policy.intake.OwnerCheckerPolicy;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;

import java.util.Optional;

/** Whether a user has worked a lead (property-create intake spec 4.3, D5-7, D5-15). Inside a transaction, as the port asks. */
@Component
@RequiredArgsConstructor
public class IntakeWork {

    private final IntakeSourcePort sources;
    private final IntakeTrailDomainRepository trail;

    public boolean hasWorked(String userId, IntakeLead lead) {
        Optional<IntakeSource> source = sources.currentSource(lead.leadId(), lead.intentCycle());
        return OwnerCheckerPolicy.hasWorked(userId, new OwnerCheckerPolicy.Work(lead.assigneeOrNull(), lead.earlierAssignees(),
                source.map(IntakeSource::performedBy).orElse(null), source.map(IntakeSource::recordedBy).orElse(null),
                trail.actorsOfLead(lead.leadId())));
    }
}
