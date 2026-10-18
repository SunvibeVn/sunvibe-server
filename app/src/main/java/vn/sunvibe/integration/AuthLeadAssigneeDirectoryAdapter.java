package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-module composition: answers acquisition-service's assignee questions with auth-service's published
 * {@link LeadAssigneeAccountUseCase}, so neither module depends on the other. Lives in app, which depends on every
 * module. It maps records and forwards; the eligibility rule and the ordering stay in auth.
 */
@Component
@RequiredArgsConstructor
public class AuthLeadAssigneeDirectoryAdapter implements LeadAssigneeDirectoryPort {

    private final LeadAssigneeAccountUseCase leadAssigneeAccountUseCase;

    @Override
    public boolean isEligibleAssignee(String userId) {
        return leadAssigneeAccountUseCase.isEligibleOwnerLeadAssignee(userId);
    }

    @Override
    public List<EligibleAssignee> listEligibleAssignees() {
        return leadAssigneeAccountUseCase.listEligibleOwnerLeadAssignees().stream()
                .map(account -> new EligibleAssignee(account.userId(), account.fullName(), account.roles()))
                .toList();
    }

    @Override
    public Map<String, String> findDisplayNames(Set<String> userIds) {
        return leadAssigneeAccountUseCase.findStaffDisplayNames(userIds);
    }
}
