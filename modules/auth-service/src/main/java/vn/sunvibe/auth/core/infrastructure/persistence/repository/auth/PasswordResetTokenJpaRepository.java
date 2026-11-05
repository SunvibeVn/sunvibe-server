package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PasswordResetTokenEntity;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data access to {@link PasswordResetTokenEntity}.
 */
@Repository
public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetTokenEntity t WHERE t.token = :token")
    Optional<PasswordResetTokenEntity> findByTokenForUpdate(@Param("token") String token);

    /** A plain value, so no token entity stays in the persistence context across the user lock. */
    @Query("SELECT t.userId FROM PasswordResetTokenEntity t WHERE t.token = :token")
    Optional<String> findUserIdByToken(@Param("token") String token);

    Optional<PasswordResetTokenEntity> findFirstByDestinationOrderByCreatedAtDesc(String destination);

    long countByDestinationAndCreatedAtAfter(String destination, Instant since);

    @Modifying
    @Query(value = "DELETE FROM password_reset_token WHERE id IN "
            + "(SELECT id FROM password_reset_token WHERE expires_at < :cutoff LIMIT :limit)", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PasswordResetTokenEntity t SET t.isUsed = true, t.usedAt = :now " +
            "WHERE t.destination = :destination AND t.isUsed = false")
    int invalidateActiveTokens(@Param("destination") String destination, @Param("now") Instant now);

}
