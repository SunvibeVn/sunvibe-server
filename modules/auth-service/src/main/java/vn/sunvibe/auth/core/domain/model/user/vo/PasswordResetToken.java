package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.shared.time.AppClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * One password-reset request: a random token plus the OTP sent with it by
 * e-mail or SMS ({@link #channel}) to {@link #destination}. Only hashes are kept:
 * the value of the token exists in {@link Issued} alone.
 * <p>
 * The OTP has only 6 digits, so each token tolerates a few wrong codes and is
 * then burned ({@link #registerFailedAttempt}); the user must request a new code.
 */
@Getter
public class PasswordResetToken {

    public static final Duration LIFETIME = Duration.ofMinutes(15);

    /** A new row and the value of its token, which goes to the user and is stored nowhere. */
    public record Issued(PasswordResetToken token, String plainToken) {
        @Override
        public String toString() {
            return "Issued[redacted]";
        }
    }

    private final Long id;
    /** SHA-256 of the value the user holds. */
    private final String token;
    private final String userId;
    private final VerificationChannel channel;
    /** E-mail address or phone number the OTP was sent to. */
    private final String destination;
    private final Instant createdAt;
    private final Instant expiresAt;
    /** The OTP as the application layer hashed it. */
    private final String otpHash;
    /** The credential version of the account at issue; null on a row from before that existed, which is invalid. */
    private final Long credentialVersion;
    private boolean used;
    private Instant usedAt;
    /** Wrong OTP codes entered for this token so far. */
    private int failedAttempts;

    private PasswordResetToken(Long id, String token, String userId, VerificationChannel channel, String destination,
                               Instant createdAt, Instant expiresAt, String otpHash, Long credentialVersion, boolean used,
                               Instant usedAt, int failedAttempts) {
        this.id = id;
        this.token = token;
        this.userId = userId;
        this.channel = channel;
        this.destination = destination;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.otpHash = otpHash;
        this.credentialVersion = credentialVersion;
        this.used = used;
        this.usedAt = usedAt;
        this.failedAttempts = failedAttempts;
    }

    /** A new token for a freshly sent OTP, valid for {@link #LIFETIME}. */
    public static Issued issue(Long id, String userId, VerificationChannel channel, String destination, String otpHash,
                               long credentialVersion) {
        Instant createdAt = AppClock.currentUTC();
        String plainToken = UUID.randomUUID().toString();
        return new Issued(new PasswordResetToken(id, hashToken(plainToken), userId, channel, destination, createdAt,
                createdAt.plus(LIFETIME), otpHash, credentialVersion, false, null, 0), plainToken);
    }

    /** Rebuilds a token from storage. */
    public static PasswordResetToken restore(Long id, String tokenHash, String userId, VerificationChannel channel,
                                             String destination, Instant createdAt, Instant expiresAt, boolean used,
                                             Instant usedAt, int failedAttempts, String otpHash, Long credentialVersion) {
        return new PasswordResetToken(id, tokenHash, userId, channel, destination, createdAt, expiresAt, otpHash,
                credentialVersion, used, usedAt, failedAttempts);
    }

    /** SHA-256 of a token value, as stored and looked up. */
    public static String hashToken(String plainToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
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
    public boolean matchesOtp(String candidateHash) {
        return candidateHash != null && otpHash != null && MessageDigest.isEqual(
                otpHash.getBytes(StandardCharsets.UTF_8), candidateHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Still the credentials it was issued for and, by e-mail, the address of the account (auth-email-access spec 4.7).
     * A token is never rebound by string, and a row without a version never matches.
     */
    public boolean isBoundTo(long accountCredentialVersion, String accountEmailKey) {
        return credentialVersion != null && credentialVersion == accountCredentialVersion
                && (channel != VerificationChannel.EMAIL || destination.equals(accountEmailKey));
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
            throw new PasswordResetTokenException(PasswordResetTokenException.Reason.TOKEN_INVALID,
                    "This reset token has already been used");
        }
        this.used = true;
        this.usedAt = now;
    }
}
