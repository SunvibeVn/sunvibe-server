package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PasswordResetTokenEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.PasswordResetTokenJpaRepository;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.common.time.AppClock;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenDomainRepository {

    private final PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;

    @Override
    public void save(PasswordResetToken passwordResetToken) {

        passwordResetTokenJpaRepository.save(PasswordResetTokenEntity.toEntity(passwordResetToken));
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return passwordResetTokenJpaRepository.findByToken(token).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public Optional<PasswordResetToken> findByEmailAndNotUsed(String email) {
        return passwordResetTokenJpaRepository.findByEmailAndIsUsedFalse(email).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public void markAsUsed(String token) {
        PasswordResetTokenEntity entity = passwordResetTokenJpaRepository.findByToken(token).orElseThrow(() -> new PasswordResetTokenException(AuthMessage.PASSWORD_RESET_TOKEN_NOT_FOUND));
        entity.setIsUsed(true);
        entity.setUsedAt(AppClock.currentUTC());
        passwordResetTokenJpaRepository.save(entity);
    }

    @Override
    public void cleanupExpiredTokens() {
        passwordResetTokenJpaRepository.deleteByExpiresAtBefore(AppClock.currentUTC());
    }

}