package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;

import java.util.Optional;
import java.util.Set;

/**
 * Cross-module composition (E05-S1 spec 5.5, ADR-009): answers brokerage-service's lead question with acquisition-service's
 * published {@link LeadIntakeUseCase} and its visibility rule. It only translates records, and joins the caller's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class AcquisitionBasisLeadAdapter implements BasisLeadPort {

    private final LeadIntakeUseCase leads;

    @Override
    public Optional<BasisLeadFacts> findVisible(String leadId, String userId, Set<String> expandedAuthorities) {
        return leads.findVisible(leadId, leads.viewerFor(userId, expandedAuthorities))
                .map(facts -> new BasisLeadFacts(facts.leadId(), facts.assignedUserIdOrNull()));
    }

    @Override
    public Optional<BasisLeadFacts> findVisibleForUpdate(String leadId, String userId, Set<String> expandedAuthorities) {
        return leads.findVisibleForUpdate(leadId, leads.viewerFor(userId, expandedAuthorities))
                .map(facts -> new BasisLeadFacts(facts.leadId(), facts.assignedUserIdOrNull()));
    }
}
