package vn.sunvibe.auth.core.infrastructure.persistence.entity.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Instant;

/**
 * Table {@code verification_codes}, created by Flyway migration
 * {@code V202610091700__add_contact_verification.sql}; keep both in sync.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "verification_codes", indexes = {
        @Index(name = "idx_verification_codes_user_channel", columnList = "user_id, channel, created_at")
})
public class VerificationCodeEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private VerificationChannel channel;

    @Column(name = "destination", nullable = false, length = 100)
    private String destination;

    /** Hex SHA-256 of "id:code"; the code itself is never stored. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

}
