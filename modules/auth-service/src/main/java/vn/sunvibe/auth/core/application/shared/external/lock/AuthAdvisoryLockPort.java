package vn.sunvibe.auth.core.application.shared.external.lock;

/**
 * Advisory locks of the send paths (auth-email-access PD-12): they hold cooldowns and caps under parallel requests, and
 * no send queues behind another. Only send paths take them, and those hold no users row lock, so they add no cycle.
 */
@FunctionalInterface
public interface AuthAdvisoryLockPort {

    /**
     * Tries the lock of {@code key} until the transaction ends, without waiting. Keys of two locks are taken user before
     * destination.
     *
     * @return false if another transaction holds it
     */
    boolean tryLock(String key);
}
