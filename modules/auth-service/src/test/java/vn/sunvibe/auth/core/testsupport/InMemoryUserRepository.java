package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.PendingAccountRepository;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written fake of the user ports, keyed by username. Lookups throw
 * {@link UserNotFoundException} like the real adapter.
 */
public class InMemoryUserRepository implements UserDomainRepository, UserLoginAttemptRepository, PendingAccountRepository {

    private final Map<String, AppUser> users = new LinkedHashMap<>();
    public int saveCount;
    public int findFullNamesByIdsCalls;

    public InMemoryUserRepository with(AppUser user) {
        users.put(user.getUsername().getValue(), user);
        return this;
    }

    @Override
    public boolean existsByUsername(String userName) {
        return users.containsKey(userName);
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.values().stream().anyMatch(u -> u.getEmail() != null && u.getEmail().getValue().equals(email));
    }

    @Override
    public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AppUser save(AppUser user) {
        saveCount++;
        users.put(user.getUsername().getValue(), user);
        return user;
    }

    @Override
    public AppUser findByUsername(String username) {
        AppUser user = users.get(username);
        if (user == null) {
            throw new UserNotFoundException("User not found with username: " + username);
        }
        return user;
    }

    @Override
    public AppUser findByEmail(String email) {
        return users.values().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().getValue().equals(email))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    @Override
    public Optional<AppUser> findById(UserId userId) {
        return users.values().stream().filter(u -> u.getId().equals(userId)).findFirst();
    }

    @Override
    public boolean addRole(UserId userId, RoleName role) {
        return findById(userId).orElseThrow().grantRole(role);
    }

    @Override
    public boolean removeRole(UserId userId, RoleName role) {
        return findById(userId).orElseThrow().revokeRole(role);
    }

    @Override
    public int incrementFailedLoginAttempts(UserId userId) {
        AppUser user = findById(userId).orElseThrow();
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        return user.getFailedLoginAttempts();
    }

    @Override
    public void lockLoginUntil(UserId userId, Instant until) {
        findById(userId).orElseThrow().setLockedUntil(until);
    }

    @Override
    public List<UserId> findUnverifiedPendingCreatedBefore(Instant cutoff, int limit) {
        return users.values().stream()
                .filter(u -> u.getUserStatus() == UserStatus.PENDING && !u.isPhoneVerified() && u.getDeletedAt() == null
                        && u.getCreatedDate().isBefore(cutoff))
                .sorted(Comparator.comparing(AppUser::getCreatedDate))
                .limit(limit)
                .map(AppUser::getId)
                .toList();
    }

    @Override
    public List<StaffAccountRow> findSignInAbleByAnyRole(Set<String> roleNames, int limit) {
        List<StaffAccountRow> rows = new java.util.ArrayList<>();
        for (AppUser user : users.values()) {
            if (user.getDeletedAt() != null || AppUser.SIGN_IN_BLOCKED_STATUSES.contains(user.getUserStatus())) {
                continue;
            }
            for (String role : user.getUserRoles()) {
                if (roleNames.contains(role)) {
                    rows.add(new StaffAccountRow(user.getId().getValue(), user.getFullName(), role));
                }
            }
        }
        // like the adapter: order by name, keep the first `limit` accounts
        rows.sort(StaffAccountRow.nameOrder());
        Set<String> kept = new java.util.LinkedHashSet<>();
        List<StaffAccountRow> result = new java.util.ArrayList<>();
        for (StaffAccountRow row : rows) {
            if (kept.contains(row.userId()) || kept.size() < limit) {
                kept.add(row.userId());
                result.add(row);
            }
        }
        return result;
    }

    /** Names of every stored account, soft-deleted or not (no filter, like the real query). */
    @Override
    public Map<String, String> findFullNamesByIds(Collection<String> ids) {
        findFullNamesByIdsCalls++;
        Map<String, String> names = new LinkedHashMap<>();
        for (AppUser user : users.values()) {
            if (ids.contains(user.getId().getValue())) {
                names.put(user.getId().getValue(), user.getFullName());
            }
        }
        return names;
    }
}
