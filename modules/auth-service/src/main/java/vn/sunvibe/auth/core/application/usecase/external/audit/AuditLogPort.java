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
     * Deletes events older than {@code cutoff}.
     *
     * @return the number of deleted events
     */
    int purgeOlderThan(Instant cutoff);

}
