package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationPurpose;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.VerificationCodeEntity;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data access to {@link VerificationCodeEntity}.
 */
public interface VerificationCodeJpaRepository extends JpaRepository<VerificationCodeEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM VerificationCodeEntity c WHERE c.userId = :userId AND c.channel = :channel " +
            "ORDER BY c.createdAt DESC")
    List<VerificationCodeEntity> findLatestForUpdate(@Param("userId") String userId,
                                                     @Param("channel") VerificationChannel channel, Limit limit);

    long countByUserIdAndChannelAndCreatedAtAfter(String userId, VerificationChannel channel, Instant since);

    @Query("SELECT c FROM VerificationCodeEntity c WHERE c.userId = :userId AND c.purpose = :purpose " +
            "ORDER BY c.createdAt DESC, c.id DESC")
    List<VerificationCodeEntity> findLatestByPurpose(@Param("userId") String userId,
                                                     @Param("purpose") VerificationPurpose purpose, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM VerificationCodeEntity c WHERE c.userId = :userId AND c.purpose = :purpose " +
            "ORDER BY c.createdAt DESC, c.id DESC")
    List<VerificationCodeEntity> findLatestByPurposeForUpdate(@Param("userId") String userId,
                                                              @Param("purpose") VerificationPurpose purpose, Limit limit);

    @Modifying
    @Query(value = "DELETE FROM verification_codes WHERE id IN "
            + "(SELECT id FROM verification_codes WHERE expires_at < :cutoff LIMIT :limit)", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    long countByUserIdAndPurposeAndCreatedAtAfter(String userId, VerificationPurpose purpose, Instant since);

    long countByDestinationAndPurposeAndCreatedAtAfter(String destination, VerificationPurpose purpose, Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE VerificationCodeEntity c SET c.consumedAt = :now " +
            "WHERE c.userId = :userId AND c.purpose = :purpose AND c.id <> :keptId AND c.consumedAt IS NULL")
    int burnOthers(@Param("userId") String userId, @Param("purpose") VerificationPurpose purpose,
                   @Param("keptId") Long keptId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE VerificationCodeEntity c SET c.consumedAt = :now " +
            "WHERE c.userId = :userId AND c.channel = :channel AND c.consumedAt IS NULL")
    int invalidateActive(@Param("userId") String userId, @Param("channel") VerificationChannel channel,
                         @Param("now") Instant now);

}
