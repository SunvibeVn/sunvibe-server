package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    Long id;
    String token;
    String userId;
    String email;
    Instant createdAt;
    Instant expiresAt;
    Boolean isUsed;
    Instant usedAt;
    String otpCode;

    private PasswordResetToken(Long id, String token, String userId, String email, Instant createdAt, Instant expiresAt, String otpCode) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.email = email;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.isUsed = false;
        this.otpCode = otpCode;
    }

    // Factory method - generate new token
    public static PasswordResetToken create(Long id, String userId, String email, String otpCode) {
        String token = UUID.randomUUID().toString();
        Instant createdAt = AppClock.currentUTC();
        Instant expiresAt = createdAt.plusSeconds(900);  // 15 minutes expiration

        return new PasswordResetToken(id, token, userId, email, createdAt, expiresAt, otpCode);
    }

    // Factory method - restore from DB
    public static PasswordResetToken restore(Long id, String token, String userId, String email, Instant createdAt, Instant expiresAt, boolean used, String otpCode) {
        PasswordResetToken passwordResetToken = new PasswordResetToken(id, token, userId, email, createdAt, expiresAt, otpCode);
        passwordResetToken.isUsed = used;
        return passwordResetToken;
    }

    // Business logic
    public boolean isExpired() {
        return AppClock.currentUTC().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired() && !isUsed;
    }

    public void markAsUsed() {
        if (this.isUsed) {
            throw new PasswordResetTokenException("This reset token has already been used");
        }
        this.isUsed = true;
    }

}
