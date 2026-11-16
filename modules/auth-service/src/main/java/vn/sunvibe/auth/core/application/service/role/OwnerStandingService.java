package vn.sunvibe.auth.core.application.service.role;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStandingUseCase;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.policy.role.OwnerStandingPolicy;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;
import vn.sunvibe.auth.core.domain.repository.user.AccountStandingRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read 4 of ADR-012 decision 2: the owner standing of a set of accounts with the eligibility of
 * {@link OwnerAccountService#ownerStanding}, through {@link OwnerStandingPolicy}. Per chunk, the standings in one
 * statement, then the role names of the accounts that may sign in in another (none when no account may).
 */
@Service
@RequiredArgsConstructor
public class OwnerStandingService implements OwnerStandingUseCase {

    private static final int CHUNK = 250;

    /** The standing of an id with no row, as {@link OwnerAccountService#ownerStanding} answers it. */
    private static final int NO_ROW = -1;

    private final AccountStandingRepository accounts;

    @Transactional(readOnly = true)
    @Override
    public Map<String, OwnerStanding> ownerStandings(Collection<String> userIds) {
        List<String> distinct = List.copyOf(new LinkedHashSet<>(List.copyOf(userIds)));
        Map<String, OwnerStanding> answered = new HashMap<>();
        for (int from = 0; from < distinct.size(); from += CHUNK) {
            answered.putAll(chunk(distinct.subList(from, Math.min(from + CHUNK, distinct.size()))));
        }
        return Map.copyOf(answered);
    }

    private Map<String, OwnerStanding> chunk(List<String> userIds) {
        List<UserId> ids = userIds.stream().map(UserId::from).toList();
        Map<UserId, AccountStanding> found = accounts.findStandings(ids);
        Set<UserId> mayAuthenticate = new LinkedHashSet<>();
        for (UserId id : ids) {
            AccountStanding row = found.get(id);
            if (row != null && OwnerStandingPolicy.mayAuthenticate(row)) {
                mayAuthenticate.add(id);
            }
        }
        Map<UserId, Set<String>> roles = mayAuthenticate.isEmpty() ? Map.of() : accounts.findRoleNames(mayAuthenticate);
        Map<String, OwnerStanding> answered = new HashMap<>();
        for (UserId id : ids) {
            AccountStanding row = found.get(id);
            answered.put(id.getValue(), row == null ? new OwnerStanding(false, NO_ROW)
                    : new OwnerStanding(mayAuthenticate.contains(id)
                            && !OwnerStandingPolicy.holdsStaffRole(roles.getOrDefault(id, Set.of())), row.signInLosses()));
        }
        return answered;
    }
}
