package vn.sunvibe.outbox;

/**
 * The outbox worker's native statements on PostgreSQL (ADR-014 decision 5; E05-S5b spec 4.5 to 4.8), positional and
 * run with {@code JdbcTemplate}, each its own auto-committed unit. Lists are bound as one SQL array, instants as
 * {@code OffsetDateTime} at UTC (plan E05S5b-P4). Every mark and release is fenced by id, lease token and status.
 */
final class OutboxSql {

    /**
     * Claims at most a batch of one consumer's due rows, oldest first, under a fresh lease. The outer predicates repeat
     * every condition on the locked row, because a row changed after the statement's snapshot is re-checked only on its
     * own conditions (SV-093). The locking subquery sits in {@code ARRAY(...)}, so it runs once: as {@code id IN (...)}
     * the planner may run it again for each outer row, and each run skips the rows this statement already updated and
     * takes the next ones, so the claim exceeds the batch. Parameters: token, lease end, consumer, now, batch, consumer,
     * now.
     */
    static final String CLAIM_DUE = """
            UPDATE outbox_events SET lease_token = ?, next_attempt_at = ?, attempts = attempts + 1
            WHERE id = ANY(ARRAY(SELECT id FROM outbox_events
                                 WHERE status = 'PENDING' AND consumer = ? AND next_attempt_at <= ?
                                 ORDER BY next_attempt_at, id LIMIT ? FOR UPDATE SKIP LOCKED))
              AND status = 'PENDING' AND consumer = ? AND next_attempt_at <= ?
            RETURNING id, event_id, event_type, event_version, consumer, aggregate_type, aggregate_id, payload,
                      correlation_id, occurred_at, attempts, last_error_code
            """;

    /** Parameters: published at, id, token. */
    static final String MARK_DONE = """
            UPDATE outbox_events SET status = 'DONE', published_at = ?, lease_token = NULL, last_error_code = NULL
            WHERE id = ? AND lease_token = ? AND status = 'PENDING'
            """;

    /** Parameters: next attempt at, error code, id, token. */
    static final String MARK_RETRY = """
            UPDATE outbox_events SET lease_token = NULL, next_attempt_at = ?, last_error_code = ?
            WHERE id = ? AND lease_token = ? AND status = 'PENDING'
            """;

    /** Parameters: error code, id, token. */
    static final String MARK_DEAD = """
            UPDATE outbox_events SET status = 'DEAD', lease_token = NULL, last_error_code = ?
            WHERE id = ? AND lease_token = ? AND status = 'PENDING'
            """;

    /**
     * Gives back claimed rows that were not attempted: due now, attempts back to their value before the claim,
     * {@code last_error_code} unchanged. Parameters: now, ids (bigint array), token.
     */
    static final String RELEASE = """
            UPDATE outbox_events SET lease_token = NULL, next_attempt_at = ?, attempts = attempts - 1
            WHERE id = ANY(?) AND lease_token = ? AND status = 'PENDING'
            RETURNING id
            """;

    /**
     * Moves due rows of consumers this instance does not know to {@code DEAD} once they waited past the queue-age
     * threshold (S5b-D7), at most 1,000 a run; the locking subquery in {@code ARRAY(...)} as in {@link #CLAIM_DUE}. An
     * empty list of known names is valid: every such row is then unknown. Parameters: stale before, known names
     * (varchar array), stale before, known names.
     */
    static final String DEAD_UNKNOWN_CONSUMERS = """
            UPDATE outbox_events SET status = 'DEAD', last_error_code = 'NO_CONSUMER', lease_token = NULL
            WHERE id = ANY(ARRAY(SELECT id FROM outbox_events
                                 WHERE status = 'PENDING' AND next_attempt_at <= ? AND NOT (consumer = ANY(?))
                                 ORDER BY next_attempt_at, id LIMIT 1000 FOR UPDATE SKIP LOCKED))
              AND status = 'PENDING' AND next_attempt_at <= ? AND NOT (consumer = ANY(?))
            RETURNING id, event_id, event_type, consumer, attempts
            """;

    /** Due rows and the oldest due time; a row due later or under a live lease is not due. Parameter: now. */
    static final String COUNT_DUE = """
            SELECT count(*) AS due, min(next_attempt_at) AS oldest FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= ?
            """;

    static final String COUNT_RETRYING = """
            SELECT count(*) FROM outbox_events WHERE status = 'PENDING' AND last_error_code IS NOT NULL
            """;

    /** Parameter: consumer names (varchar array). */
    static final String COUNT_RETRYING_OF = """
            SELECT count(*) FROM outbox_events
            WHERE status = 'PENDING' AND last_error_code IS NOT NULL AND consumer = ANY(?)
            """;

    static final String COUNT_DEAD = """
            SELECT count(*) FROM outbox_events WHERE status = 'DEAD'
            """;

    /** Deletes at most 1,000 {@code DONE} rows published before the cutoff. Parameter: cutoff. */
    static final String DELETE_DONE_BEFORE = """
            DELETE FROM outbox_events
            WHERE id IN (SELECT id FROM outbox_events WHERE status = 'DONE' AND published_at < ?
                         ORDER BY published_at LIMIT 1000)
            """;

    private OutboxSql() {
    }
}
