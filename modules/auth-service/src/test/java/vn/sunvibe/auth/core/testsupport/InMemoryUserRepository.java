package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
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
 * Hand-written fake of the user ports, keyed by user id. Lookups throw
 * {@link UserNotFoundException} like the real adapter.
 */
public class InMemoryUserRepository implements UserDomainRepository, UserLoginAttemptRepository, PendingAccountRepository {

    private final Map<String, AppUser> users = new LinkedHashMap<>();
    public int saveCount;
    /** The order of lock and token calls, shared with the token fakes of a test. */
    public final List<String> calls = new java.util.ArrayList<>();
    public int loginSuccessWrites;
    public int findFullNamesByIdsCalls;

    public InMemoryUserRepository with(AppUser user) {
        users.put(user.getId().getValue(), user);
        return this;
    }

    @Override
    public boolean existsByUsername(String userName) {
        return users.values().stream().anyMatch(u -> userName.equals(u.usernameValue()));
    }

    @Override
    public Optional<AppUser> findByEmailKey(String emailKey) {
        return users.values().stream().filter(u -> emailKey.equals(u.getEmailKey())).findFirst();
    }

    @Override
    public EmailOccupancy emailOccupancy(String emailKey, UserId selfOrNull) {
        List<AppUser> holders = users.values().stream()
                .filter(u -> !u.getId().equals(selfOrNull))
                .filter(u -> emailKey.equals(u.getEmailKey()) || (u.getEmailKey() == null && u.getEmail() != null
                        && emailKey.equals(u.getEmail().getValue().strip().toLowerCase(java.util.Locale.ROOT))))
                .toList();
        if (holders.isEmpty()) {
            return EmailOccupancy.FREE;
        }
        return holders.stream().anyMatch(AppUser::isEmailVerified) ? EmailOccupancy.VERIFIED : EmailOccupancy.OTHER;
    }

    @Override
    public boolean existsByPhoneKey(String phoneKey) {
        return findByPhoneKey(phoneKey).isPresent();
    }

    @Override
    public Optional<AppUser> findByPhoneKey(String phoneKey) {
        return users.values().stream().filter(u -> phoneKey.equals(u.getPhoneKey())).findFirst();
    }

    @Override
    public Optional<AppUser> findKeylessByUsername(String username) {
        return users.values().stream()
                .filter(u -> u.getPhoneKey() == null && username.equals(u.usernameValue())).findFirst();
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
    public AppUser create(AppUser user) {
        users.put(user.getId().getValue(), user);
        return user;
    }

    @Override
    public AppUser save(LockedUser locked) {
        saveCount++;
        users.put(locked.user().getId().getValue(), locked.user());
        return locked.user();
    }

    @Override
    public Optional<LockedUser> findByIdForSecurity(UserId userId) {
        calls.add("lock-user");
        return findById(userId).map(LockedUser::new);
    }

    @Override
    public void lockForSessionRefresh(UserId userId) {
        calls.add("share-user");
    }

    @Override
    public int incrementReauthFailures(UserId userId) {
        AppUser user = findById(userId).orElseThrow();
        user.setReauthFailedAttempts(user.getReauthFailedAttempts() + 1);
        return user.getReauthFailedAttempts();
    }

    @Override
    public void lockReauthUntil(UserId userId, Instant until) {
        findById(userId).orElseThrow().setReauthLockedUntil(until);
    }

    @Override
    public void clearReauthFailures(UserId userId) {
        AppUser user = findById(userId).orElseThrow();
        user.setReauthFailedAttempts(0);
        user.setReauthLockedUntil(null);
    }

    @Override
    public void recordLoginSuccess(UserId userId, Instant when) {
        loginSuccessWrites++;
        AppUser user = findById(userId).orElseThrow();
        user.setLastLogin(when);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    @Override
    public void activateIfPending(UserId userId) {
        AppUser user = findById(userId).orElseThrow();
        if (user.getUserStatus() == UserStatus.PENDING) {
            user.setUserStatus(UserStatus.ACTIVE);
        }
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
                .filter(u -> u.getUserStatus() == UserStatus.PENDING && !u.isPhoneVerified()
                        && u.getEmailVerifiedAt() == null && u.getDeletedAt() == null
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
