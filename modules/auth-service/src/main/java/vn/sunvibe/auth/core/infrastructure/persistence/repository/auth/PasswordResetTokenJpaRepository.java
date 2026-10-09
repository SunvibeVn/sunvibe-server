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

@Repository
public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetTokenEntity t WHERE t.token = :token")
    Optional<PasswordResetTokenEntity> findByTokenForUpdate(@Param("token") String token);

    Optional<PasswordResetTokenEntity> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PasswordResetTokenEntity t SET t.isUsed = true, t.usedAt = :now " +
            "WHERE t.email = :email AND t.isUsed = false")
    int invalidateActiveTokens(@Param("email") String email, @Param("now") Instant now);

    @Modifying
    void deleteByExpiresAtBefore(Instant time);

}
