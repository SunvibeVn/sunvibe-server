package vn.sunvibe.auth.core.domain.model.auth;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationPurpose;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
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
    private final VerificationPurpose purpose;
    /** Phone number or e-mail the code was sent to. */
    private final String destination;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int failedAttempts;
    /** Set once the code is used or burned. */
    private Instant consumedAt;
    /** For a bind code: the credential version it was issued for; an older one is void. */
    private final Long credentialVersion;

    private VerificationCode(Long id, String userId, VerificationChannel channel, VerificationPurpose purpose,
                             String destination, String codeHash, Instant createdAt, Instant expiresAt,
                             int failedAttempts, Instant consumedAt, Long credentialVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.failedAttempts = failedAttempts;
        this.consumedAt = consumedAt;
        this.credentialVersion = credentialVersion;
    }

    /** A new code sent to {@code destination}; only its hash, made by the application layer, is kept. */
    public static VerificationCode issue(Long id, String userId, VerificationChannel channel, String destination,
                                         String codeHash, Instant now, Duration lifetime) {
        return new VerificationCode(id, userId, channel, VerificationPurpose.CONTACT, destination, codeHash, now,
                now.plus(lifetime), 0, null, null);
    }

    /** A new code for an address the user wants on the account, tied to the credential version the password proved. */
    public static VerificationCode issueForBind(Long id, String userId, String destination, String codeHash,
                                                long credentialVersion, Instant now, Duration lifetime) {
        return new VerificationCode(id, userId, VerificationChannel.EMAIL, VerificationPurpose.EMAIL_BIND, destination,
                codeHash, now, now.plus(lifetime), 0, null, credentialVersion);
    }

    /** Rebuilds a code from storage. */
    public static VerificationCode load(Long id, String userId, VerificationChannel channel, VerificationPurpose purpose,
                                        String destination, String codeHash, Instant createdAt, Instant expiresAt,
                                        int failedAttempts, Instant consumedAt, Long credentialVersion) {
        return new VerificationCode(id, userId, channel, purpose, destination, codeHash, createdAt, expiresAt,
                failedAttempts, consumedAt, credentialVersion);
    }

    /** Not used, not burned and not expired. */
    public boolean isUsable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    /** Compares hashes in constant time. */
    public boolean matches(String candidateHash) {
        return candidateHash != null && MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.US_ASCII), candidateHash.getBytes(StandardCharsets.US_ASCII));
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
}
