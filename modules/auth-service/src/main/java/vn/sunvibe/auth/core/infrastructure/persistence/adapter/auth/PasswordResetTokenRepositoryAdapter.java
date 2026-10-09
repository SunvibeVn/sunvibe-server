package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PasswordResetTokenEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.PasswordResetTokenJpaRepository;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
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
    public Optional<PasswordResetToken> findByTokenForUpdate(String token) {
        return passwordResetTokenJpaRepository.findByTokenForUpdate(token).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public Optional<PasswordResetToken> findLatestByDestination(String destination) {
        return passwordResetTokenJpaRepository.findFirstByDestinationOrderByCreatedAtDesc(destination)
                .map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public void invalidateActiveTokens(String destination, Instant now) {
        passwordResetTokenJpaRepository.invalidateActiveTokens(destination, now);
    }

    @Override
    public long countIssuedSince(String destination, Instant since) {
        return passwordResetTokenJpaRepository.countByDestinationAndCreatedAtAfter(destination, since);
    }

    @Override
    public void cleanupExpiredTokens() {
        passwordResetTokenJpaRepository.deleteByExpiresAtBefore(AppClock.currentUTC());
    }

}
