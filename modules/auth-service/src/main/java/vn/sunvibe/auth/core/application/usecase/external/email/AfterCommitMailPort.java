package vn.sunvibe.auth.core.application.usecase.external.email;

/**
 * Sends mail once the caller's transaction committed, off the request thread (auth-email-access PD-7), so the answer
 * does not wait for SMTP and a rollback sends nothing. The task handles its own failure; a full queue costs a warn line.
 */
public interface AfterCommitMailPort {

    /** Runs {@code send} after the commit, or at once outside a transaction. Never throws. */
    default void dispatch(Runnable send) {
        dispatch(send, () -> { });
    }

    /** As {@link #dispatch(Runnable)}, and {@code onQueueFull} runs in place of {@code send} when the pool takes no more. */
    void dispatch(Runnable send, Runnable onQueueFull);
}
