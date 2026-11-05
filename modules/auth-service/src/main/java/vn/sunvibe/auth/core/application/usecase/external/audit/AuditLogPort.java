package vn.sunvibe.auth.core.application.usecase.external.audit;

import java.time.Instant;

/**
 * Security audit log of the auth module.
 */
public interface AuditLogPort {

    /**
     * Records an event, written a moment later by a thread of its own. Kept even if the caller's transaction rolls
     * back (most security events end in an exception), and never throws or waits: a broken audit log must not break
     * sign-in.
     */
    void record(AuthAuditEvent event);

    /**
     * Records a success event (e.g. a role granted) in the caller's transaction, on its connection, so the event and
     * the change commit together: a change that rolls back leaves no row, and a committed one is never without its
     * row. Outside a transaction it is written at once. A failed write fails the change.
     */
    void recordAfterCommit(AuthAuditEvent event);

    /**
     * Deletes events older than {@code cutoff}.
     *
     * @return the number of deleted events
     */
    int purgeOlderThan(Instant cutoff);

}
