package vn.sunvibe.auth.core.infrastructure.persistence.entity.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;

import java.time.Instant;

/**
 * Table {@code password_reset_token}; its Flyway definition is in
 * {@code V202610091500__add_login_lockout_and_reset_attempts.sql}.
 */
@Entity
@Table(name = "PASSWORD_RESET_TOKEN", indexes = {
        @Index(name = "idx_password_reset_token_token", columnList = "token", unique = true),
        @Index(name = "idx_password_reset_token_email", columnList = "email"),
        @Index(name = "idx_password_reset_token_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetTokenEntity {

    @Id
    private Long id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "otp_code", nullable = false)
    private String otpCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_used", nullable = false)
    private Boolean isUsed;

    @Column(name = "used_at")
    private Instant usedAt;

    /** Wrong OTP codes entered for this token. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    public static PasswordResetTokenEntity toEntity(PasswordResetToken passwordResetToken) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(passwordResetToken.getId());
        entity.setToken(passwordResetToken.getToken());
        entity.setUserId(passwordResetToken.getUserId());
        entity.setEmail(passwordResetToken.getEmail());
        entity.setOtpCode(passwordResetToken.getOtpCode());
        entity.setCreatedAt(passwordResetToken.getCreatedAt());
        entity.setExpiresAt(passwordResetToken.getExpiresAt());
        entity.setIsUsed(passwordResetToken.isUsed());
        entity.setUsedAt(passwordResetToken.getUsedAt());
        entity.setFailedAttempts(passwordResetToken.getFailedAttempts());
        return entity;
    }

    public static PasswordResetToken toDomain(PasswordResetTokenEntity entity) {
        return PasswordResetToken.restore(
                entity.getId(),
                entity.getToken(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                Boolean.TRUE.equals(entity.getIsUsed()),
                entity.getUsedAt(),
                entity.getFailedAttempts(),
                entity.getOtpCode());
    }

}
