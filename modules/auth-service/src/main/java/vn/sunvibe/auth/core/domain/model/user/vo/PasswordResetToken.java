package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.common.time.AppClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One password-reset request: a random token plus the OTP sent with it by
 * e-mail or SMS ({@link #channel}) to {@link #destination}.
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
    private final VerificationChannel channel;
    /** E-mail address or phone number the OTP was sent to. */
    private final String destination;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String otpCode;
    private boolean used;
    private Instant usedAt;
    /** Wrong OTP codes entered for this token so far. */
    private int failedAttempts;

    private PasswordResetToken(Long id, String token, String userId, VerificationChannel channel, String destination,
                               Instant createdAt, Instant expiresAt, String otpCode, boolean used, Instant usedAt,
                               int failedAttempts) {
        this.id = id;
        this.token = token;
        this.userId = userId;
        this.channel = channel;
        this.destination = destination;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.otpCode = otpCode;
        this.used = used;
        this.usedAt = usedAt;
        this.failedAttempts = failedAttempts;
    }

    /** A new token for a freshly sent OTP, valid for {@link #LIFETIME}. */
    public static PasswordResetToken create(Long id, String userId, VerificationChannel channel, String destination,
                                            String otpCode) {
        Instant createdAt = AppClock.currentUTC();
        return new PasswordResetToken(id, UUID.randomUUID().toString(), userId, channel, destination, createdAt,
                createdAt.plus(LIFETIME), otpCode, false, null, 0);
    }

    /** Rebuilds a token from storage. */
    public static PasswordResetToken restore(Long id, String token, String userId, VerificationChannel channel,
                                             String destination, Instant createdAt, Instant expiresAt, boolean used,
                                             Instant usedAt, int failedAttempts, String otpCode) {
        return new PasswordResetToken(id, token, userId, channel, destination, createdAt, expiresAt, otpCode, used,
                usedAt, failedAttempts);
    }

    /** Rebuilds an unused-so-far e-mail token with no failed attempts recorded. */
    public static PasswordResetToken restore(Long id, String token, String userId, String email, Instant createdAt,
                                             Instant expiresAt, boolean used, String otpCode) {
        return restore(id, token, userId, VerificationChannel.EMAIL, email, createdAt, expiresAt, used, null, 0, otpCode);
    }

    /** True once {@link #expiresAt} has passed. */
    public boolean isExpired() {
        return AppClock.currentUTC().isAfter(expiresAt);
    }

    /** Not expired, not used and not burned. */
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

    /**
     * Consumes the token after a successful reset.
     *
     * @throws PasswordResetTokenException if it was already used or burned
     */
    public void markAsUsed(Instant now) {
        if (this.used) {
            throw new PasswordResetTokenException("This reset token has already been used");
        }
        this.used = true;
        this.usedAt = now;
    }
}
