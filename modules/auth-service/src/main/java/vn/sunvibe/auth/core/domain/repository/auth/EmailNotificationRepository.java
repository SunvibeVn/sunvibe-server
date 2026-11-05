package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;

import java.time.Instant;
import java.util.List;

/**
 * Persistence of notices still to be sent.
 */
public interface EmailNotificationRepository {

    /** Inserts a new notice or updates an existing one. */
    void save(EmailNotification notification);

    /**
     * Claims up to {@code limit} due notices for this worker: locked rows are skipped, so two workers never take one, and
     * the claimed rows move to {@code claimedUntil}, so a worker that dies leaves them for a later run. Needs a
     * transaction, which the caller commits before sending.
     */
    List<EmailNotification> claimDue(Instant now, int limit, Instant claimedUntil);

    void delete(Long id);

}
