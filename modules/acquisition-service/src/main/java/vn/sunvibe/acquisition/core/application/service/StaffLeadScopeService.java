package vn.sunvibe.acquisition.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.application.usecase.StaffLeadScope;
import vn.sunvibe.acquisition.core.application.usecase.StaffLeadScopeUseCase;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadCycle;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - StaffLeadScopeService
 * <p>
 * The roles go through {@link LeadAccessPolicy}, the one mapping of the lead queue; a SALE's leads are read by the
 * existing cycle query over the assignee index, in every status, without loading a lead. Read-only: it writes nothing,
 * and with no access it reads nothing (fail closed). Joined by the caller's transaction when it has one.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaffLeadScopeService implements StaffLeadScopeUseCase {

    private final LeadAccessPolicy accessPolicy;
    private final OwnerLeadDomainRepository leads;

    @Override
    public StaffLeadScope scopeFor(String userId, Set<String> expandedAuthorities) {
        Optional<LeadAccess> access = accessPolicy.accessFor(expandedAuthorities);
        if (access.isEmpty()) {
            return new StaffLeadScope(false, Set.of());
        }
        if (access.get() == LeadAccess.ALL) {
            return new StaffLeadScope(true, Set.of());
        }
        // brokerage-agreement BR-18: an agreement outlives its lead's state, so a Sale's scope holds every status.
        return new StaffLeadScope(false, leads.findCycles(EnumSet.allOf(OwnerLeadStatus.class), Optional.of(userId))
                .stream().map(LeadCycle::leadId).collect(Collectors.toSet()));
    }
}
