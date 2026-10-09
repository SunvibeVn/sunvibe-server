package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles WHERE u.username = :username")
    Optional<UserEntity> findByUsername(@Param("username") String username);

}
