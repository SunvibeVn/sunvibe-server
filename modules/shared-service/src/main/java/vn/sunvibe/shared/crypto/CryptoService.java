package vn.sunvibe.shared.crypto;

/**
 * Encrypts/decrypts a single sensitive string field at rest, and computes
 * a deterministic "blind index" for exact-match lookups against encrypted
 * data (since randomized encryption can't be compared or uniquely
 * constrained directly in SQL).
 */
public interface CryptoService {

    /** Encrypts {@code plaintext}; returns a base64 string, different every call. */
    String encrypt(String plaintext);

    /** Decrypts a value previously returned by {@link #encrypt(String)}. */
    String decrypt(String ciphertext);

    /**
     * Deterministic, keyed (HMAC) hash of {@code value}, hex-encoded.
     * Safe to store alongside the encrypted value and index/compare on,
     * without exposing the plaintext or being practically precomputable
     * the way an unkeyed hash of a small keyspace (e.g. ID card numbers)
     * would be.
     */
    String blindIndex(String value);
}
