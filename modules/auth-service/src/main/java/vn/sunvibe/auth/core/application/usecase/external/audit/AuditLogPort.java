package vn.sunvibe.auth.core.application.usecase.external.audit;

import java.time.Instant;

/**
 * Security audit log of the auth module.
 */
public interface AuditLogPort {

    /**
     * Records an event. Kept even if the caller's transaction rolls back (most
     * security events end in an exception), and never throws: a broken audit
     * log must not break sign-in.
     */
    void record(AuthAuditEvent event);

    /**
     * Records a success event (e.g. a role granted) only once the caller's transaction commits, so a change that
     * rolls back leaves no audit row. Outside a transaction it is recorded at once. Never throws.
     */
    void recordAfterCommit(AuthAuditEvent event);

    /**
     * Deletes events older than {@code cutoff}.
     *
     * @return the number of deleted events
     */
    int purgeOlderThan(Instant cutoff);

}
