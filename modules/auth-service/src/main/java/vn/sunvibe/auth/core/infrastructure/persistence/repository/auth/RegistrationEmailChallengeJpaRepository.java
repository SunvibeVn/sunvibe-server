package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RegistrationEmailChallengeEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data access to {@link RegistrationEmailChallengeEntity}.
 */
public interface RegistrationEmailChallengeJpaRepository extends JpaRepository<RegistrationEmailChallengeEntity, Long> {

    Optional<RegistrationEmailChallengeEntity> findFirstByEmailKeyOrderByCreatedAtDescIdDesc(String emailKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM RegistrationEmailChallengeEntity c WHERE c.emailKey = :emailKey ORDER BY c.createdAt DESC, c.id DESC")
    List<RegistrationEmailChallengeEntity> findLatestForUpdate(@Param("emailKey") String emailKey, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationEmailChallengeEntity> findByTokenHash(String tokenHash);

    long countByEmailKeyAndCreatedAtAfter(String emailKey, Instant since);

    @Modifying
    @Query(value = "DELETE FROM registration_email_challenges WHERE id IN "
            + "(SELECT id FROM registration_email_challenges WHERE expires_at < :cutoff "
            + "AND (token_expires_at IS NULL OR token_expires_at < :cutoff) LIMIT :limit)", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RegistrationEmailChallengeEntity c SET c.consumedAt = :now " +
            "WHERE c.emailKey = :emailKey AND c.consumedAt IS NULL")
    int burnActiveCodes(@Param("emailKey") String emailKey, @Param("now") Instant now);

}
