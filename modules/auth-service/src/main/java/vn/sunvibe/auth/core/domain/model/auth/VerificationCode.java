package vn.sunvibe.auth.core.domain.model.auth;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A one-time code proving that the user controls a phone number or e-mail.
 * <p>
 * The code is bound to the destination it was sent to: if the account's e-mail
 * changes afterwards, the code no longer verifies it. Only a hash of the code is
 * kept. A few wrong guesses burn the code.
 */
@Getter
public class VerificationCode {

    private final Long id;
    private final String userId;
    private final VerificationChannel channel;
    /** Phone number or e-mail the code was sent to. */
    private final String destination;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int failedAttempts;
    /** Set once the code is used or burned. */
    private Instant consumedAt;

    private VerificationCode(Long id, String userId, VerificationChannel channel, String destination, String codeHash,
                             Instant createdAt, Instant expiresAt, int failedAttempts, Instant consumedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.failedAttempts = failedAttempts;
        this.consumedAt = consumedAt;
    }

    /** A new code sent to {@code destination}; only its salted hash is kept. */
    public static VerificationCode issue(Long id, String userId, VerificationChannel channel, String destination,
                                         String code, Instant now, Duration lifetime) {
        return new VerificationCode(id, userId, channel, destination, hash(id, code), now, now.plus(lifetime), 0, null);
    }

    /** Rebuilds a code from storage. */
    public static VerificationCode load(Long id, String userId, VerificationChannel channel, String destination,
                                        String codeHash, Instant createdAt, Instant expiresAt, int failedAttempts,
                                        Instant consumedAt) {
        return new VerificationCode(id, userId, channel, destination, codeHash, createdAt, expiresAt, failedAttempts, consumedAt);
    }

    /** Not used, not burned and not expired. */
    public boolean isUsable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    /** Compares hashes in constant time. */
    public boolean matches(String code) {
        return code != null && MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.US_ASCII), hash(id, code).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Counts a wrong code; the {@code maxAttempts}-th one burns it.
     *
     * @return true if the code can no longer be used
     */
    public boolean registerFailedAttempt(int maxAttempts, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts && consumedAt == null) {
            consumedAt = now;
        }
        return consumedAt != null;
    }

    /**
     * Uses the code up after a successful verification.
     *
     * @throws IllegalStateException if it was already used or burned
     */
    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("Verification code already used.");
        }
        consumedAt = now;
    }

    /** SHA-256 of the code salted with the row id, so equal codes hash differently. */
    private static String hash(Long id, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((id + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
