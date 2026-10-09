package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.auth.RefreshTokenEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository.auth.JpaRefreshTokenRepository;

@Service
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenDomainRepository {

    private final JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Override
    public void saveRefreshToken(RefreshToken refreshToken) {
        jpaRefreshTokenRepository.save(RefreshTokenEntity.toEntity(refreshToken));
    }

    @Override
    public RefreshToken findRefreshTokenByTokenHash(byte[] hash) {
        return RefreshTokenEntity.toDomain(jpaRefreshTokenRepository.findByTokenHash(hash).orElseThrow());
    }

}
