package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadAssigneePort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadAssigneeFacts;

import java.util.Collection;
import java.util.List;

/**
 * Cross-module composition (E05-S6 spec S6-D10, S6-D17, ADR-009): answers brokerage-service's assignees of a set of basis
 * leads (group (ii) of the listing brief, for listing's commands) with acquisition-service's viewer-less
 * {@link LeadIntakeUseCase#systemFacts}. It only translates records, and joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class AcquisitionBasisLeadAssigneeAdapter implements BasisLeadAssigneePort {

    private final LeadIntakeUseCase leads;

    @Override
    public List<LeadAssigneeFacts> assigneesOf(Collection<String> leadIds) {
        return leads.systemFacts(leadIds).values().stream()
                .map(facts -> new LeadAssigneeFacts(facts.leadId(), facts.assignedUserIdOrNull()))
                .toList();
    }
}
