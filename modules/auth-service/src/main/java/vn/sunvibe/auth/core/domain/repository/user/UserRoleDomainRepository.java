package vn.sunvibe.auth.core.domain.repository.user;


import java.util.Set;

public interface UserRoleDomainRepository {

    Set<String> findAllByUsername(String username);

}
