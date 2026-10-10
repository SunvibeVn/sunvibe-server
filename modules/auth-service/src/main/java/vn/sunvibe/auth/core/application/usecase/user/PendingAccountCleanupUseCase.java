package vn.sunvibe.auth.core.application.usecase.user;

/**
 * Housekeeping of accounts that were registered but never verified.
 */
public interface PendingAccountCleanupUseCase {

    /**
     * Deactivates (soft-deletes) accounts still PENDING with an unverified phone
     * longer than the allowed time after registration.
     *
     * @return the number of accounts deactivated
     */
    int execute();

}
