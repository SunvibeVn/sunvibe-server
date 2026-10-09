package vn.sunvibe.auth.core.domain.repository.user;


import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

public interface UserDomainRepository {

    boolean existsByUsername(String userName);

    boolean existsByEmail(String email);

    boolean existsByIdCardNumberHash(String idCardNumberHash);

    AppUser save(AppUser user);

    AppUser findByUsername(String username);

    AppUser findByEmail(String email);
}
