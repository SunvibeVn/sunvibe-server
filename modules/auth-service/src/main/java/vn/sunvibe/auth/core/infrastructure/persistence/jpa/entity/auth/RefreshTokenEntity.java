package vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "REFRESH_TOKEN", uniqueConstraints = {@UniqueConstraint(name = "uk_token_hash", columnNames = "token_hash")})
public class RefreshTokenEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Lob
    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(length = 200)
    private String fingerprint;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "use_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TokenStatus status;

    public static RefreshTokenEntity toEntity(RefreshToken domain) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setFamilyId(domain.getFamilyId());
        entity.setTokenHash(domain.getTokenHash());
        entity.setFingerprint(domain.getFingerprint());
        entity.setIssuedAt(domain.getIssuedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setUsedAt(domain.getUsedAt());
        entity.setRevokedAt(domain.getRevokedAt());
        entity.setStatus(domain.getStatus());
        return entity;
    }

    public static RefreshToken toDomain(RefreshTokenEntity entity) {
        RefreshToken domain = new RefreshToken();
        domain.setId(entity.getId());
        domain.setUserId(entity.getUserId());
        domain.setFamilyId(entity.getFamilyId());
        domain.setTokenHash(entity.getTokenHash());
        domain.setFingerprint(entity.getFingerprint());
        domain.setIssuedAt(entity.getIssuedAt());
        domain.setExpiresAt(entity.getExpiresAt());
        domain.setUsedAt(entity.getUsedAt());
        domain.setRevokedAt(entity.getRevokedAt());
        domain.setStatus(entity.getStatus());
        return domain;
    }

}
