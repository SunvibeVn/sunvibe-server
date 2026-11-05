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

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.emailKey = :emailKey AND u.deletedAt IS NULL")
    Optional<UserEntity> findByEmailKey(@Param("emailKey") String emailKey);

    /**
     * True for a row where the address is verified, false for any other row that holds it, through
     * {@code uq_users_email_key} or {@code idx_users_keyless_email}, whose expression this repeats. Native: JPQL cannot
     * state the C collation. {@code self} is an id no row has when nobody is left out.
     */
    @Query(value = "select email_key is not null and email_verified_at is not null from users"
            + " where deleted_at is null and id <> :self and (email_key = :emailKey"
            + " or (email is not null and email_key is null and lower(btrim(email) collate \"C\") = :emailKey))",
            nativeQuery = true)
    List<Boolean> findEmailHolders(@Param("emailKey") String emailKey, @Param("self") String self);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.phoneKey = :phoneKey AND u.deletedAt IS NULL")
    boolean existsByPhoneKey(@Param("phoneKey") String phoneKey);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.phoneKey = :phoneKey AND u.deletedAt IS NULL")
    Optional<UserEntity> findByPhoneKey(@Param("phoneKey") String phoneKey);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.username = :username AND u.phoneKey IS NULL AND u.deletedAt IS NULL")
    Optional<UserEntity> findKeylessByUsername(@Param("username") String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<UserEntity> findActiveById(@Param("id") String id);

    /**
     * The row lock of every security command (auth-email-access spec 5.4): native, because
     * {@code PESSIMISTIC_WRITE} takes {@code FOR UPDATE}, which blocks the key-share lock of a refresh token insert.
     */
    @Query(value = "select id from users where id = :id and deleted_at is null for no key update", nativeQuery = true)
    Optional<String> lockForSecurity(@Param("id") String id);

    /** {@code FOR SHARE} for a session refresh: scalar, so no entity outlives the lock with an old state. */
    @Query(value = "select id from users where id = :id for share", nativeQuery = true)
    Optional<String> lockForShare(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.lastLogin = :when, u.failedLoginAttempts = 0, u.lockedUntil = NULL WHERE u.id = :id")
    int recordLoginSuccess(@Param("id") String id, @Param("when") Instant when);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.userStatus = :active WHERE u.id = :id AND u.userStatus = :pending")
    int activateIfPending(@Param("id") String id, @Param("active") UserStatus active, @Param("pending") UserStatus pending);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.reauthFailedAttempts = u.reauthFailedAttempts + 1 WHERE u.id = :id")
    int incrementReauthFailures(@Param("id") String id);

    @Query("SELECT u.reauthFailedAttempts FROM UserEntity u WHERE u.id = :id")
    int findReauthFailures(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.reauthLockedUntil = :until WHERE u.id = :id")
    int lockReauthUntil(@Param("id") String id, @Param("until") Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.reauthFailedAttempts = 0, u.reauthLockedUntil = NULL WHERE u.id = :id")
    int clearReauthFailures(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    int incrementFailedLoginAttempts(@Param("id") String id);

    @Query("SELECT u.failedLoginAttempts FROM UserEntity u WHERE u.id = :id")
    int findFailedLoginAttempts(@Param("id") String id);

    @Query("SELECT u.id FROM UserEntity u WHERE u.userStatus = :pending AND u.phoneVerifiedAt IS NULL " +
            "AND u.emailVerifiedAt IS NULL AND u.deletedAt IS NULL AND u.createdDate < :cutoff ORDER BY u.createdDate")
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
