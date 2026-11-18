package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.usecase.StaffLeadScope;
import vn.sunvibe.acquisition.core.application.usecase.StaffLeadScopeUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadScopePort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadScopeFacts;

import java.util.Set;

/**
 * Cross-module composition (E05-S7 spec S7-D2, ADR-009): answers brokerage-service's question of the caller's lead
 * scope with acquisition-service's published {@link StaffLeadScopeUseCase}, the one mapping from roles to leads. It
 * only translates the record, passes the signed-in caller unchanged and joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class AcquisitionBasisLeadScopeAdapter implements BasisLeadScopePort {

    private final StaffLeadScopeUseCase scopes;

    @Override
    public LeadScopeFacts scopeOf(String userId, Set<String> expandedAuthorities) {
        StaffLeadScope scope = scopes.scopeFor(userId, expandedAuthorities);
        return new LeadScopeFacts(scope.allLeads(), scope.assignedLeadIds());
    }
}
