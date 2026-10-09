package vn.sunvibe.auth.core.application.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Creates refresh-token secrets and the hash under which they are stored.
 * <p>
 * Secrets are 256 random bits, so a plain (unsalted, fast) SHA-256 is enough:
 * nothing can be brute-forced, and the hash only keeps a database leak from
 * handing out usable tokens.
 */
public final class RefreshTokenSecrets {

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenSecrets() {
    }

    /** A new URL-safe secret to hand to the client. */
    public static String generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256 of {@code secret}: the value stored and looked up. */
    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
