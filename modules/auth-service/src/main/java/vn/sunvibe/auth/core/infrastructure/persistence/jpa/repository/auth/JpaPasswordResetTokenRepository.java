package vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.auth.PasswordResetTokenEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface JpaPasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByToken(String token);
    Optional<PasswordResetTokenEntity> findByEmailAndIsUsedFalse(String email);
    void deleteByExpiresAtBefore(Instant time);  // ✅ Cleanup expired
}