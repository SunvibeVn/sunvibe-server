package vn.sunvibe.acquisition.core.application.shared.external.security;

/**
 * Keyed one-way hash of personal technical data (IP address, user agent) kept as proof of consent. The raw value is
 * never stored (acquisition module brief BR-2).
 */
public interface ConsentHasher {

    /** The hash of {@code value} as 64 lower-case hexadecimal characters; the same value always gives the same hash. */
    String hash(String value);
}
