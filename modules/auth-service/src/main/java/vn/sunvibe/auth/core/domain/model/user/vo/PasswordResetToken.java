package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.common.time.AppClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One password-reset request: a random token plus the OTP e-mailed with it.
 * <p>
 * The OTP has only 6 digits, so each token tolerates a few wrong codes and is
 * then burned ({@link #registerFailedAttempt}); the user must request a new code.
 */
@Getter
public class PasswordResetToken {

    public static final Duration LIFETIME = Duration.ofMinutes(15);

    private final Long id;
    private final String token;
    private final String userId;
    private final String email;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String otpCode;
    private boolean used;
    private Instant usedAt;
    /** Wrong OTP codes entered for this token so far. */
    private int failedAttempts;

    private PasswordResetToken(Long id, String token, String userId, String email, Instant createdAt, Instant expiresAt,
                               String otpCode, boolean used, Instant usedAt, int failedAttempts) {
        this.id = id;
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.otpCode = otpCode;
        this.used = used;
        this.usedAt = usedAt;
        this.failedAttempts = failedAttempts;
    }

    // Factory method - generate new token
    public static PasswordResetToken create(Long id, String userId, String email, String otpCode) {
        Instant createdAt = AppClock.currentUTC();
        return new PasswordResetToken(id, UUID.randomUUID().toString(), userId, email, createdAt,
                createdAt.plus(LIFETIME), otpCode, false, null, 0);
    }

    // Factory method - restore from DB
    public static PasswordResetToken restore(Long id, String token, String userId, String email, Instant createdAt,
                                             Instant expiresAt, boolean used, Instant usedAt, int failedAttempts,
                                             String otpCode) {
        return new PasswordResetToken(id, token, userId, email, createdAt, expiresAt, otpCode, used, usedAt, failedAttempts);
    }

    // Factory method - restore from DB, no failed attempts recorded
    public static PasswordResetToken restore(Long id, String token, String userId, String email, Instant createdAt,
                                             Instant expiresAt, boolean used, String otpCode) {
        return restore(id, token, userId, email, createdAt, expiresAt, used, null, 0, otpCode);
    }

    // Business logic
    public boolean isExpired() {
        return AppClock.currentUTC().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired() && !used;
    }

    /** Compares in constant time, so response timing reveals nothing about the code. */
    public boolean matchesOtp(String code) {
        return code != null && otpCode != null && MessageDigest.isEqual(
                otpCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Counts a wrong OTP. On the {@code maxAttempts}-th one the token is burned.
     *
     * @return true if the token can no longer be used
     */
    public boolean registerFailedAttempt(int maxAttempts, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts && !used) {
            used = true;
            usedAt = now;
        }
        return used;
    }

    public void markAsUsed(Instant now) {
        if (this.used) {
            throw new PasswordResetTokenException("This reset token has already been used");
        }
        this.used = true;
        this.usedAt = now;
    }
}
