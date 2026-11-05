package vn.sunvibe.auth.core.infrastructure.persistence.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Table {@code registration_email_challenges}, created by Flyway migration
 * {@code V202611051009__create_registration_email_challenges.sql}; keep both in sync.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "registration_email_challenges")
public class RegistrationEmailChallengeEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "email_key", nullable = false, length = 254)
    private String emailKey;

    /** The hash of the code; the code itself is never stored. */
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
