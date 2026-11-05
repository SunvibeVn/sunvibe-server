package vn.sunvibe.property.core.application.shared.lock;

/**
 * Serializes the creates of a ward, so two parallel completions cannot both pass the duplicate signal (property-create intake spec
 * 5.3, E-9). A lock lives until the end of the current transaction, so both methods need an active transaction and throw
 * {@link IllegalStateException} without one.
 */
public interface WardCreateLockPort {

    /** Takes the lock of this ward without waiting. */
    boolean tryLock(String wardCode);

    /**
     * Bounds what the rest of the transaction waits for a row lock to 3 seconds. A completion calls it after the signal and before
     * the role grant, which locks the {@code users} row under the lead, intake and ward locks; never at transaction start, where
     * it would also cap the lead wait.
     */
    void boundOwnerRowWait();
}
