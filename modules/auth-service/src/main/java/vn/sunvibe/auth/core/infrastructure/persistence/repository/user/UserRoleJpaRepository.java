package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data access to {@link UserRoleEntity}.
 */
@Repository
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UUID> {

    Set<UserRoleEntity> findByNameIn(Collection<String> names);

}
