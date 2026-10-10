package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RefreshTokenEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data access to {@link RefreshTokenEntity}. Bulk updates clear the
 * persistence context so later reads see the new state.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /** {@code SELECT ... FOR UPDATE}: concurrent refreshes of one token run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.status = :revoked, t.revokedAt = :now, t.revokeReason = :reason " +
            "WHERE t.familyId = :familyId AND t.status <> :revoked")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") RevokeReason reason,
                     @Param("now") Instant now, @Param("revoked") TokenStatus revoked);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.status = :revoked, t.revokedAt = :now, t.revokeReason = :reason " +
            "WHERE t.userId = :userId AND t.status <> :revoked")
    int revokeAllForUser(@Param("userId") String userId, @Param("reason") RevokeReason reason,
                         @Param("now") Instant now, @Param("revoked") TokenStatus revoked);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

}
