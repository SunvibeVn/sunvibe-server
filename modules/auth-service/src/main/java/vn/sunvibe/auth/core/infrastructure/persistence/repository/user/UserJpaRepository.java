package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data access to {@link UserEntity}. Every lookup skips soft-deleted
 * rows ({@code deletedAt IS NULL}).
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.username = :username AND u.deletedAt IS NULL")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.idCardNumberHash = :idCardNumberHash AND u.deletedAt IS NULL")
    boolean existsByIdCardNumberHash(@Param("idCardNumberHash") String idCardNumberHash);

    @Query("SELECT u FROM UserEntity u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.username = :username AND u.deletedAt IS NULL")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<UserEntity> findActiveById(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    int incrementFailedLoginAttempts(@Param("id") String id);

    @Query("SELECT u.failedLoginAttempts FROM UserEntity u WHERE u.id = :id")
    int findFailedLoginAttempts(@Param("id") String id);

    @Query("SELECT u.id FROM UserEntity u WHERE u.userStatus = :pending AND u.phoneVerifiedAt IS NULL " +
            "AND u.deletedAt IS NULL AND u.createdDate < :cutoff ORDER BY u.createdDate")
    List<String> findUnverifiedPendingIds(@Param("pending") UserStatus pending, @Param("cutoff") Instant cutoff,
                                          Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.lockedUntil = :until WHERE u.id = :id")
    int lockLoginUntil(@Param("id") String id, @Param("until") Instant until);

}
