package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.manual.RefreshTokenEntityMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.RefreshTokenJpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenDomainRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final RefreshTokenEntityMapper refreshTokenEntityMapper;

    @Override
    public void save(RefreshToken refreshToken) {
        refreshTokenJpaRepository.save(refreshTokenEntityMapper.toEntity(refreshToken));
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return refreshTokenJpaRepository.findByTokenHashForUpdate(tokenHash).map(refreshTokenEntityMapper::toDomain);
    }

    @Override
    public void revokeFamily(UUID familyId, RevokeReason reason, Instant now) {
        refreshTokenJpaRepository.revokeFamily(familyId, reason, now, TokenStatus.REVOKED);
    }

    @Override
    public void revokeAllForUser(UserId userId, RevokeReason reason, Instant now) {
        refreshTokenJpaRepository.revokeAllForUser(userId.getValue(), reason, now, TokenStatus.REVOKED);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return refreshTokenJpaRepository.deleteExpiredBefore(cutoff);
    }

}
