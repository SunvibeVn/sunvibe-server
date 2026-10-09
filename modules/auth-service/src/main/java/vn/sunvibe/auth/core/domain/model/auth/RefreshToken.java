package vn.sunvibe.auth.core.domain.model.auth;

import lombok.*;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RefreshToken {

    private Long id;
    private String userId;
    private UUID familyId; // To support rotate
    private byte[] tokenHash;   // SHA-256 of refresh_plain
    private String fingerprint;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
    private TokenStatus status;

    // Business logic methods
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markUsed() {
        this.status = TokenStatus.USED;
    }

    public void revoke() {
        this.status = TokenStatus.REVOKED;
    }

}
