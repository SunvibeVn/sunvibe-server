package vn.sunvibe.auth.core.domain.repository.user;


import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.util.Optional;

public interface UserDomainRepository {

    boolean existsByUsername(String userName);

    boolean existsByEmail(String email);

    boolean existsByIdCardNumber(IdCardNumber idCardNumber);

    AppUser save(AppUser user);

    AppUser findByUsername(String username);

    AppUser findByEmail(String email);

    /** The user with this id, unless it does not exist or is soft-deleted. */
    Optional<AppUser> findById(UserId userId);
}
