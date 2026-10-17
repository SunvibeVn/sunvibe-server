package vn.sunvibe.acquisition.core.application.usecase;

/**
 * Housekeeping of the owner intake idempotency keys, which grow by one row per request that sends a key.
 */
public interface IdempotencyKeyCleanupUseCase {

    /**
     * Deletes the keys stored more than 24 hours ago; such a key no longer replays anything. Leads are never touched.
     *
     * @return the number of deleted keys
     */
    int purgeExpired();
}
