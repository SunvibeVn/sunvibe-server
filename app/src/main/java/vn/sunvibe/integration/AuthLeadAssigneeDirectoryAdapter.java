package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;

/**
 * Cross-module composition: answers acquisition-service's assignee question with auth-service's published
 * {@link LeadAssigneeAccountUseCase}, so neither module depends on the other. Lives in app, which depends on every
 * module.
 */
@Component
@RequiredArgsConstructor
public class AuthLeadAssigneeDirectoryAdapter implements LeadAssigneeDirectoryPort {

    private final LeadAssigneeAccountUseCase leadAssigneeAccountUseCase;

    @Override
    public boolean isEligibleAssignee(String userId) {
        return leadAssigneeAccountUseCase.isEligibleOwnerLeadAssignee(userId);
    }
}
