package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PasswordResetTokenEntity;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByToken(String token);
    Optional<PasswordResetTokenEntity> findByEmailAndIsUsedFalse(String email);
    void deleteByExpiresAtBefore(Instant time);  // ✅ Cleanup expired
}