package vn.sunvibe.acquisition.core.application.shared.lock;

/**
 * Serializes concurrent intake submissions so that two requests with the same idempotency key, or the same phone,
 * cannot both pass the duplicate checks (acquisition-owner-intake BR-10). A lock lives until the end of the current
 * transaction, so both methods need an active transaction and throw {@link IllegalStateException} without one.
 */
public interface IntakeLockPort {

    /** Waits until no other transaction holds the lock for this idempotency key, then holds it. */
    void lockIdempotencyKey(String key);

    /** Waits until no other transaction holds the lock for this normalized phone, then holds it. */
    void lockPhone(String phoneNormalized);
}
