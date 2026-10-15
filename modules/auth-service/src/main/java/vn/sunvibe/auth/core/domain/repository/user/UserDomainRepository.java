package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.util.Optional;

/**
 * Persistence of user accounts. Every lookup ignores soft-deleted accounts, so
 * their phone number, e-mail and ID card number can be registered again.
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
}
