package vn.sunvibe.auth.core.infrastructure.persistence.converter.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RefreshTokenEntity;

/**
 * Maps {@link RefreshToken} to and from {@link RefreshTokenEntity}; the domain
 * side is rebuilt through {@link RefreshToken#load}.
 */
@Component
public class RefreshTokenEntityMapper {

    public RefreshTokenEntity toEntity(RefreshToken domain) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId().getValue());
        entity.setFamilyId(domain.getFamilyId());
        entity.setTokenHash(domain.getTokenHash());
        entity.setStatus(domain.getStatus());
        entity.setIssuedAt(domain.getIssuedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setSessionExpiresAt(domain.getSessionExpiresAt());
        entity.setUsedAt(domain.getUsedAt());
        entity.setRevokedAt(domain.getRevokedAt());
        entity.setRevokeReason(domain.getRevokeReason());
        entity.setUserAgent(domain.getUserAgent());
        entity.setIpAddress(domain.getIpAddress());
        return entity;
    }

    public RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.load(
                entity.getId(),
                UserId.from(entity.getUserId()),
                entity.getFamilyId(),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getSessionExpiresAt(),
                entity.getUserAgent(),
                entity.getIpAddress(),
                entity.getStatus(),
                entity.getUsedAt(),
                entity.getRevokedAt(),
                entity.getRevokeReason());
    }

}
