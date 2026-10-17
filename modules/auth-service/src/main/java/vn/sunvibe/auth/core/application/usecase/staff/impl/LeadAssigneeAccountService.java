package vn.sunvibe.auth.core.application.usecase.staff.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * Who may look after owner leads: an account that may sign in and holds SALE or SALE_LEADER (acquisition-owner-lead-queue
 * spec 5.4). Answers false for anything else; it never reveals why.
 */
@Service
@RequiredArgsConstructor
public class LeadAssigneeAccountService implements LeadAssigneeAccountUseCase {

    private final UserDomainRepository userRepository;

    @Transactional(readOnly = true)
    @Override
    public boolean isEligibleOwnerLeadAssignee(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        // findById skips soft-deleted accounts
        return userRepository.findById(UserId.from(userId))
                .filter(AppUser::canAuthenticate)
                .filter(user -> user.getUserRoles().contains(RoleName.SALE.name())
                        || user.getUserRoles().contains(RoleName.SALE_LEADER.name()))
                .isPresent();
    }
}
