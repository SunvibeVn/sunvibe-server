package vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;

import java.time.Instant;

@Entity
@Table(name = "PASSWORD_RESET_TOKEN", indexes = {
        @Index(name = "idx_token", columnList = "token", unique = true),
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetTokenEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String otpCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Boolean isUsed;

    private Instant usedAt;

    public static PasswordResetTokenEntity toEntity(PasswordResetToken passwordResetToken) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(passwordResetToken.getId());
        entity.setToken(passwordResetToken.getToken());
        entity.setUserId(passwordResetToken.getUserId());
        entity.setEmail(passwordResetToken.getEmail());
        entity.setOtpCode(passwordResetToken.getOtpCode());
        entity.setCreatedAt(passwordResetToken.getCreatedAt());
        entity.setIsUsed(passwordResetToken.getIsUsed());
        entity.setUsedAt(passwordResetToken.getUsedAt());
        return entity;
    }

    public static PasswordResetToken toDomain (PasswordResetTokenEntity passwordResetTokenEntity) {
        PasswordResetToken domain = new PasswordResetToken();
        domain.setId(passwordResetTokenEntity.getId());
        domain.setToken(passwordResetTokenEntity.getToken());
        domain.setUserId(passwordResetTokenEntity.getUserId());
        domain.setEmail(passwordResetTokenEntity.getEmail());
        domain.setOtpCode(passwordResetTokenEntity.getOtpCode());
        domain.setCreatedAt(passwordResetTokenEntity.getCreatedAt());
        domain.setIsUsed(passwordResetTokenEntity.getIsUsed());
        domain.setUsedAt(passwordResetTokenEntity.getUsedAt());
        return domain;
    }

}
