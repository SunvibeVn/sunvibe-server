package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.auth.PasswordResetTokenEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository.auth.JpaPasswordResetTokenRepository;
import vn.sunvibe.auth.core.infrastructure.time.Clock;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.common.time.AppClock;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenDomainRepository {

    private final JpaPasswordResetTokenRepository jpaPasswordResetTokenRepository;

    @Override
    public void save(PasswordResetToken passwordResetToken) {

        jpaPasswordResetTokenRepository.save(PasswordResetTokenEntity.toEntity(passwordResetToken));
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaPasswordResetTokenRepository.findByToken(token).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public Optional<PasswordResetToken> findByEmailAndNotUsed(String email) {
        return jpaPasswordResetTokenRepository.findByEmailAndIsUsedFalse(email).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public void markAsUsed(String token) {
        PasswordResetTokenEntity entity = jpaPasswordResetTokenRepository.findByToken(token).orElseThrow(() -> new PasswordResetTokenException(AuthMessage.PASSWORD_RESET_TOKEN_NOT_FOUND));
        entity.setIsUsed(true);
        entity.setUsedAt(AppClock.currentUTC());
        jpaPasswordResetTokenRepository.save(entity);
    }

    @Override
    public void cleanupExpiredTokens() {
        jpaPasswordResetTokenRepository.deleteByExpiresAtBefore(AppClock.currentUTC());
    }

}