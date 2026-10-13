package vn.sunvibe.auth.core.infrastructure.persistence.entity.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Table {@code registration_phone_challenges}, created by Flyway migration
 * {@code V202610121000__create_registration_phone_challenges.sql}; keep both in sync.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "registration_phone_challenges", indexes = {
        @Index(name = "idx_registration_phone_challenges_phone", columnList = "phone, created_at")
})
public class RegistrationPhoneChallengeEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

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

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Hex SHA-256 of the registration token; the token itself is never stored. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "registered_at")
    private Instant registeredAt;

}
