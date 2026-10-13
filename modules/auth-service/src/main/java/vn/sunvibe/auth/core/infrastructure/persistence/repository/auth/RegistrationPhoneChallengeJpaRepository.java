package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RegistrationPhoneChallengeEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data access to {@link RegistrationPhoneChallengeEntity}.
 */
public interface RegistrationPhoneChallengeJpaRepository extends JpaRepository<RegistrationPhoneChallengeEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM RegistrationPhoneChallengeEntity c WHERE c.phone = :phone ORDER BY c.createdAt DESC")
    List<RegistrationPhoneChallengeEntity> findLatestForUpdate(@Param("phone") String phone, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationPhoneChallengeEntity> findByTokenHash(String tokenHash);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RegistrationPhoneChallengeEntity c SET c.consumedAt = :now " +
            "WHERE c.phone = :phone AND c.consumedAt IS NULL")
    int invalidateActiveCodes(@Param("phone") String phone, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RegistrationPhoneChallengeEntity c SET c.tokenExpiresAt = :now " +
            "WHERE c.phone = :phone AND c.tokenHash IS NOT NULL AND c.registeredAt IS NULL AND c.tokenExpiresAt > :now")
    int revokeActiveTokens(@Param("phone") String phone, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RegistrationPhoneChallengeEntity c WHERE c.expiresAt < :cutoff " +
            "AND (c.tokenExpiresAt IS NULL OR c.tokenExpiresAt < :cutoff)")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

}
