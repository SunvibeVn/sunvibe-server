package vn.sunvibe.auth.core.application.usecase.staff.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccount;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who may look after owner leads: an account that may sign in and holds SALE or SALE_LEADER (acquisition-owner-lead-queue
 * spec 5.4). Answers false for anything else; it never reveals why. The list and the check share one rule
 * ({@link AppUser#SIGN_IN_BLOCKED_STATUSES}, not soft-deleted, one of the two roles). Names are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadAssigneeAccountService implements LeadAssigneeAccountUseCase {

    /** The most accounts one list returns (acquisition-owner-lead-queue-ui spec 4.1). */
    static final int MAX_LISTED_ASSIGNEES = 500;

    private static final Set<String> ASSIGNEE_ROLES = Set.of(RoleName.SALE.name(), RoleName.SALE_LEADER.name());

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
                .filter(user -> user.getUserRoles().stream().anyMatch(ASSIGNEE_ROLES::contains))
                .isPresent();
    }

    @Transactional(readOnly = true)
    @Override
    public List<LeadAssigneeAccount> listEligibleOwnerLeadAssignees() {
        // One more than the cap: getting 501 accounts back is how the service knows the list is cut, while exactly
        // 500 is a complete list.
        List<StaffAccountRow> rows = userRepository.findSignInAbleByAnyRole(ASSIGNEE_ROLES, MAX_LISTED_ASSIGNEES + 1);
        // The repository cuts with this same order; sorting again keeps the service correct for any repository.
        List<StaffAccountRow> sorted = rows.stream().sorted(StaffAccountRow.nameOrder()).toList();

        Map<String, String> namesById = new LinkedHashMap<>();
        Map<String, Set<String>> rolesById = new LinkedHashMap<>();
        for (StaffAccountRow row : sorted) {
            if (!ASSIGNEE_ROLES.contains(row.roleName())) {
                continue;
            }
            namesById.putIfAbsent(row.userId(), row.fullName());
            rolesById.computeIfAbsent(row.userId(), id -> new TreeSet<>()).add(row.roleName());
        }

        List<LeadAssigneeAccount> accounts = namesById.entrySet().stream()
                .limit(MAX_LISTED_ASSIGNEES)
                .map(entry -> new LeadAssigneeAccount(entry.getKey(), entry.getValue(),
                        Collections.unmodifiableSet(rolesById.get(entry.getKey()))))
                .toList();
        if (namesById.size() > MAX_LISTED_ASSIGNEES) {
            log.warn("Owner lead assignee list is cut at its limit of {} accounts; later accounts are not listed",
                    MAX_LISTED_ASSIGNEES);
        }
        return accounts;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, String> findStaffDisplayNames(Collection<String> userIds) {
        if (userIds == null) {
            return Map.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String id : userIds) {
            if (Objects.nonNull(id) && !id.isBlank()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findFullNamesByIds(ids);
    }
}
