package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.domain.repository.user.UserRoleDomainRepository;

import java.util.Set;

@Repository
public class UserRoleRepositoryAdapter implements UserRoleDomainRepository {
    @Override
    public Set<String> findAllByUsername(String username) {
        return Set.of();
    }
}
