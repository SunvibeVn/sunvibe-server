package vn.sunvibe.acquisition.core.application.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.application.usecase.LeadAssigneeQueryUseCase;

import java.util.List;

/**
 * Use Case Implementation - LeadAssigneeQueryService
 * <p>
 * Forwards to the staff directory port: it already sorts the accounts (by name, then id) and caps them, so the order
 * and the size are kept as they come. Read-only, and it writes no access log: the list holds no lead data.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeadAssigneeQueryService implements LeadAssigneeQueryUseCase {

    private final LeadAssigneeDirectoryPort assigneeDirectory;

    @Override
    public List<EligibleAssignee> listEligible() {
        return assigneeDirectory.listEligibleAssignees();
    }
}
