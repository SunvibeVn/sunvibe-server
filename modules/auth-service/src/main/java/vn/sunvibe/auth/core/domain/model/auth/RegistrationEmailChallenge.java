package vn.sunvibe.auth.core.domain.model.auth;

import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Proof, before an account exists, that someone controls a mailbox (auth-email-access spec 4.7).
 * <p>
 * Step 1: a one-time code is mailed. Step 2: the right code is exchanged for a single-use registration token. Step 3:
 * registration presents the token and the challenge is closed. Only hashes of the code and the token are kept; a few
 * wrong guesses burn the code. A challenge of one table never answers another (auth-email-access BR-19).
 */
@Getter
public class RegistrationEmailChallenge {

    private final Long id;
    private final String emailKey;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int failedAttempts;
    /** Set once the code is used or burned. */
    private Instant consumedAt;
    /** Set when the right code was entered and the address was free. */
    private Instant verifiedAt;
    private String tokenHash;
    private Instant tokenExpiresAt;
    /** Set when registration used the token up. */
    private Instant registeredAt;

    private RegistrationEmailChallenge(Long id, String emailKey, String codeHash, Instant createdAt, Instant expiresAt,
                                       int failedAttempts, Instant consumedAt, Instant verifiedAt, String tokenHash,
                                       Instant tokenExpiresAt, Instant registeredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.emailKey = Objects.requireNonNull(emailKey, "emailKey");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.failedAttempts = failedAttempts;
        this.consumedAt = consumedAt;
        this.verifiedAt = verifiedAt;
        this.tokenHash = tokenHash;
        this.tokenExpiresAt = tokenExpiresAt;
        this.registeredAt = registeredAt;
    }

    /** A new challenge for {@code emailKey}; only the hash of its code, made by the application layer, is kept. */
    public static RegistrationEmailChallenge issue(Long id, String emailKey, String codeHash, Instant now, Duration lifetime) {
        return new RegistrationEmailChallenge(id, emailKey, codeHash, now, now.plus(lifetime), 0, null, null,
                null, null, null);
    }

    /** Rebuilds a challenge from storage. */
    public static RegistrationEmailChallenge load(Long id, String emailKey, String codeHash, Instant createdAt,
                                                  Instant expiresAt, int failedAttempts, Instant consumedAt,
                                                  Instant verifiedAt, String tokenHash, Instant tokenExpiresAt,
                                                  Instant registeredAt) {
        return new RegistrationEmailChallenge(id, emailKey, codeHash, createdAt, expiresAt, failedAttempts, consumedAt,
                verifiedAt, tokenHash, tokenExpiresAt, registeredAt);
    }

    /** The code is not used, not burned and not expired. */
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

    /** The right code, for an address that cannot register: the code is used up and no token comes of it. */
    public void consume(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }

    /**
     * The right code was entered: uses the code up and attaches a registration token.
     *
     * @param token         the plain token handed to the client; only its hash is kept
     * @param tokenLifetime how long the token may be presented to registration
     * @throws IllegalStateException if the code is no longer usable
     */
    public void verify(String token, Instant now, Duration tokenLifetime) {
        if (!isUsable(now)) {
            throw new IllegalStateException("Verification code is no longer usable.");
        }
        consumedAt = now;
        verifiedAt = now;
        tokenHash = hashToken(token);
        tokenExpiresAt = now.plus(tokenLifetime);
    }

    /** A token was issued, is not used yet and has not expired. */
    public boolean isTokenUsable(Instant now) {
        return tokenHash != null && registeredAt == null && tokenExpiresAt != null && now.isBefore(tokenExpiresAt);
    }

    /**
     * Registration used the token up.
     *
     * @throws IllegalStateException if the token is missing, used or expired
     */
    public void markRegistered(Instant now) {
        if (!isTokenUsable(now)) {
            throw new IllegalStateException("Registration token is no longer usable.");
        }
        registeredAt = now;
    }

    /** SHA-256 of a registration token, as stored and looked up. */
    public static String hashToken(String token) {
        return sha256(token);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
