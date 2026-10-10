package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
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
     * Inserts or updates the account, with its roles.
     *
     * @return the account as stored
     */
    AppUser save(AppUser user);

    /** @throws UserNotFoundException if no account has this username */
    AppUser findByUsername(String username);

    /** @throws UserNotFoundException if no account has this e-mail */
    AppUser findByEmail(String email);

    /** The user with this id, unless it does not exist or is soft-deleted. */
    Optional<AppUser> findById(UserId userId);
}
