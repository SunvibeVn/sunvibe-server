package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.exception.user.IdentifierConflictException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence of user accounts. Every lookup ignores soft-deleted accounts, so
 * their phone number, e-mail and ID card number can be registered again, except
 * {@link #findFullNamesByIds} (acquisition-owner-lead-queue-ui C-35), which keeps the names of staff who left.
 */
public interface UserDomainRepository {

    /** Whether a live row stores exactly this username: the raw check that covers a row without a key. */
    boolean existsByUsername(String userName);

    /** Whether a live row holds this canonical phone. */
    boolean existsByPhoneKey(String phoneKey);

    /** @param email lower-case, as stored by {@code Email} */
    boolean existsByEmail(String email);

    boolean existsByIdCardNumber(IdCardNumber idCardNumber);

    /**
     * The only insert (auth-email-access I-4): stores a new account with its roles, at once, so a lost race for an
     * identifier surfaces here.
     *
     * @return the account as stored
     * @throws IdentifierConflictException if a unique index on an identifier refused it; the transaction is lost
     * @throws IllegalStateException if a role of the account has no row in the roles table
     */
    AppUser create(AppUser user);

    /**
     * The only update (auth-email-access I-4): writes an account read under its row lock, at once. The stored roles
     * are kept as they are (roles change only through {@link #addRole} and {@link #removeRole}).
     *
     * @return the account as stored
     * @throws IdentifierConflictException if a unique index on an identifier refused it; the transaction is lost
     */
    AppUser save(LockedUser user);

    /** The live account that holds this canonical email, verified or not. */
    Optional<AppUser> findByEmailKey(String emailKey);

    /**
     * Who holds this canonical email among live rows, by its key or, on a row without a key, by the stored address
     * folded the same way (auth-email-access I-3).
     *
     * @param selfOrNull an account to leave out: an address it holds itself is no obstacle
     */
    EmailOccupancy emailOccupancy(String emailKey, UserId selfOrNull);

    /** The live account with this canonical phone. */
    Optional<AppUser> findByPhoneKey(String phoneKey);

    /** The live account that stores exactly this username and has no phone key: the legacy lookup only. */
    Optional<AppUser> findKeylessByUsername(String username);

    /** The user with this id, unless it does not exist or is soft-deleted. */
    Optional<AppUser> findById(UserId userId);

    /**
     * Locks the row of a live account until the transaction ends, then reads it again, also when the persistence
     * context already holds it (auth-email-access spec 5.4). The lock is {@code FOR NO KEY UPDATE}: {@code FOR UPDATE}
     * would block every foreign key insert into refresh_tokens.
     *
     * @return the fresh account; empty if it does not exist or is soft-deleted
     */
    Optional<LockedUser> findByIdForSecurity(UserId userId);

    /**
     * Holds the row against a change of credentials for the rest of the transaction, waiting 3 seconds at most: two
     * refreshes do not wait for each other, and a reset waits for the refresh, so it revokes the token it issues.
     * The holder must not write users, or two refreshes would deadlock on the upgrade (auth-email-access spec 5.4).
     */
    void lockForSessionRefresh(UserId userId);

    /**
     * Adds one role to the account with a single-row write, so concurrent role changes cannot undo each other.
     *
     * @return true if the role was added, false if the account already held it
     */
    boolean addRole(UserId userId, RoleName role);

    /**
     * Removes one role from the account with a single-row write.
     *
     * @return true if the role was removed, false if the account did not hold it
     */
    boolean removeRole(UserId userId, RoleName role);

    /**
     * Accounts that may sign in (not soft-deleted, status not in {@code AppUser.SIGN_IN_BLOCKED_STATUSES}) and hold at
     * least one of the given roles: one row per account and matching role, so an account with two matching roles
     * appears twice. Only the matching roles are listed, never the account's others. The accounts are ordered by
     * {@link StaffAccountRow#nameOrder()} and cut to the first {@code limit} accounts (not rows).
     */
    List<StaffAccountRow> findSignInAbleByAnyRole(Set<String> roleNames, int limit);

    /**
     * The full name of each account with one of these ids, including soft-deleted, locked and role-less accounts.
     * Unknown ids are absent. Empty input returns an empty map without a query.
     */
    Map<String, String> findFullNamesByIds(Collection<String> ids);
}
