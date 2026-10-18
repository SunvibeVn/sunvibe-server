package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data access to {@link UserEntity}. Every lookup skips soft-deleted
 * rows ({@code deletedAt IS NULL}), except findNamesByIds (acquisition-owner-lead-queue-ui C-35).
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

    /**
     * One row per account and matching role, as scalars: the role collection of the entity is never loaded here
     * (it cascades ALL, so a filtered JOIN FETCH could cut it on the next save). No SQL limit: an account with
     * several roles gives several rows, so the caller cuts by account.
     */
    @Query("SELECT new vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow(u.id, u.fullName, r.name) " +
            "FROM UserEntity u JOIN u.userRoles r " +
            "WHERE r.name IN :roles AND u.deletedAt IS NULL AND u.userStatus NOT IN :blocked")
    List<StaffAccountRow> findSignInAbleStaffRows(@Param("roles") Collection<String> roles,
                                                  @Param("blocked") Collection<UserStatus> blocked);

    /** Deliberately no {@code deletedAt} filter: names stay readable for soft-deleted and locked accounts. */
    @Query("SELECT new vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository$UserName(" +
            "u.id, u.fullName) FROM UserEntity u WHERE u.id IN :ids")
    List<UserName> findNamesByIds(@Param("ids") Collection<String> ids);

    /** Id and full name of one account. */
    record UserName(String id, String fullName) {
    }

}
