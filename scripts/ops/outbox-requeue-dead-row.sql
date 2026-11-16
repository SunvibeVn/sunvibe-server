-- Puts one DEAD row of the transactional outbox back to PENDING once its cause is fixed (ADR-014 decision 8;
-- scripts/ops/README.md section 11): due now, attempts 0, no lease, no error code. Prints the id it requeued.
-- psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v row_id=<id> -f scripts/ops/outbox-requeue-dead-row.sql
UPDATE outbox_events
SET status = 'PENDING', attempts = 0, next_attempt_at = now(), lease_token = NULL, last_error_code = NULL
WHERE status = 'DEAD' AND id = :'row_id'::bigint
RETURNING id;
