package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RefreshTokenEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.RefreshTokenJpaRepository;

@Service
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenDomainRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public void saveRefreshToken(RefreshToken refreshToken) {
        refreshTokenJpaRepository.save(RefreshTokenEntity.toEntity(refreshToken));
    }

    @Override
    public RefreshToken findRefreshTokenByTokenHash(byte[] hash) {
        return RefreshTokenEntity.toDomain(refreshTokenJpaRepository.findByTokenHash(hash).orElseThrow());
    }

}
