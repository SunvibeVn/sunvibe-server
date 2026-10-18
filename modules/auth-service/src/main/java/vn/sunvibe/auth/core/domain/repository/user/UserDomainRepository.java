package vn.sunvibe.auth.core.domain.repository.user;

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

    boolean existsByUsername(String userName);

    /** @param email lower-case, as stored by {@code Email} */
    boolean existsByEmail(String email);

    boolean existsByIdCardNumber(IdCardNumber idCardNumber);

    /**
     * Inserts or updates the account. A new account is stored with its roles; for an existing one the stored
     * roles are kept as they are (roles change only through {@link #addRole} and {@link #removeRole}), so saving
     * an aggregate loaded before a role change cannot undo it.
     *
     * @return the account as stored
     * @throws IllegalStateException if a role of a new account has no row in the roles table
     */
    AppUser save(AppUser user);

    /** @throws UserNotFoundException if no account has this username */
    AppUser findByUsername(String username);

    /** @throws UserNotFoundException if no account has this e-mail */
    AppUser findByEmail(String email);

    /** The user with this id, unless it does not exist or is soft-deleted. */
    Optional<AppUser> findById(UserId userId);

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
