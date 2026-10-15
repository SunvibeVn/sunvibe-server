package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data access to {@link UserRoleEntity}.
 */
@Repository
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UUID> {

    Set<UserRoleEntity> findByNameIn(Collection<String> names);

    Optional<UserRoleEntity> findByName(String name);

    /** @return 1 if the row was inserted, 0 if the user already held the role */
    @Modifying
    @Query(value = "INSERT INTO user_roles (user_id, role_id) SELECT :userId, :roleId WHERE NOT EXISTS "
            + "(SELECT 1 FROM user_roles WHERE user_id = :userId AND role_id = :roleId)", nativeQuery = true)
    int insertUserRole(@Param("userId") String userId, @Param("roleId") UUID roleId);

    /** @return 1 if the row was deleted, 0 if the user did not hold the role */
    @Modifying
    @Query(value = "DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId", nativeQuery = true)
    int deleteUserRole(@Param("userId") String userId, @Param("roleId") UUID roleId);

}
