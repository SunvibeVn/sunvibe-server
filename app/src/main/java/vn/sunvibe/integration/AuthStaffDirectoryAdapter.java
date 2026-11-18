package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.account.StaffDirectoryPort;

import java.util.Collection;
import java.util.Map;

/**
 * Cross-module composition (E05-S7 spec S7-D7, S7-D15; ADR-009): answers brokerage-service's staff names with
 * auth-service's published {@link LeadAssigneeAccountUseCase#findStaffDisplayNames}. Brokerage calls it only for the
 * staff actors of the history rows of an agreement the caller may read (its basis lead is visible to them), never for
 * the owner, and the names never reach HTTP by themselves. An empty question costs no lookup.
 */
@Component
@RequiredArgsConstructor
public class AuthStaffDirectoryAdapter implements StaffDirectoryPort {

    private final LeadAssigneeAccountUseCase accounts;

    @Override
    public Map<String, String> displayNames(Collection<String> userIds) {
        return userIds.isEmpty() ? Map.of() : accounts.findStaffDisplayNames(userIds);
    }
}
