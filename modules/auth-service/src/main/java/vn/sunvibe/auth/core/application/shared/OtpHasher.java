package vn.sunvibe.auth.core.application.shared;

/**
 * Hashes a one-time code for storage, with a server key over the row id and the code (auth-email-access spec 4.7): a plain
 * hash of a million possible codes falls to whoever reads the table (SV-096). The domain takes the result as a value, so it
 * stays free of keys.
 */
@FunctionalInterface
public interface OtpHasher {

    /** The stored form of {@code code} on the row {@code rowId}; equal codes differ between rows. */
    String hash(long rowId, String code);
}
