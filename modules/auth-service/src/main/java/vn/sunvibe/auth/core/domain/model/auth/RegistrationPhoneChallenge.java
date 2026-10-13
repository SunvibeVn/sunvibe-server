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
 * Proof, before an account exists, that someone controls a phone number.
 * <p>
 * Step 1: a one-time code is sent to the phone. Step 2: the right code is
 * exchanged for a single-use registration token. Step 3: registration presents
 * the token and the challenge is closed. Only hashes of the code and the token
 * are kept; a few wrong guesses burn the code.
 */
@Getter
public class RegistrationPhoneChallenge {

    private final Long id;
    private final String phone;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int failedAttempts;
    /** Set once the code is used or burned. */
    private Instant consumedAt;
    /** Set when the right code was entered. */
    private Instant verifiedAt;
    private String tokenHash;
    private Instant tokenExpiresAt;
    /** Set when registration used the token up. */
    private Instant registeredAt;

    private RegistrationPhoneChallenge(Long id, String phone, String codeHash, Instant createdAt, Instant expiresAt,
                                       int failedAttempts, Instant consumedAt, Instant verifiedAt, String tokenHash,
                                       Instant tokenExpiresAt, Instant registeredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.phone = Objects.requireNonNull(phone, "phone");
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

    /** A new challenge for {@code phone}; only the salted hash of {@code code} is kept. */
    public static RegistrationPhoneChallenge issue(Long id, String phone, String code, Instant now, Duration lifetime) {
        return new RegistrationPhoneChallenge(id, phone, hashCode(id, code), now, now.plus(lifetime), 0, null, null,
                null, null, null);
    }

    /** Rebuilds a challenge from storage. */
    public static RegistrationPhoneChallenge load(Long id, String phone, String codeHash, Instant createdAt,
                                                  Instant expiresAt, int failedAttempts, Instant consumedAt,
                                                  Instant verifiedAt, String tokenHash, Instant tokenExpiresAt,
                                                  Instant registeredAt) {
        return new RegistrationPhoneChallenge(id, phone, codeHash, createdAt, expiresAt, failedAttempts, consumedAt,
                verifiedAt, tokenHash, tokenExpiresAt, registeredAt);
    }

    /** The code is not used, not burned and not expired. */
    public boolean isUsable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    /** Compares hashes in constant time. */
    public boolean matches(String code) {
        return code != null && MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.US_ASCII), hashCode(id, code).getBytes(StandardCharsets.US_ASCII));
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

    /** SHA-256 of the code salted with the row id, so equal codes hash differently. */
    private static String hashCode(Long id, String code) {
        return sha256(id + ":" + code);
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
