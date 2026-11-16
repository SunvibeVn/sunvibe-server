-- Lists the DEAD rows of the transactional outbox (ADR-014 decision 8; scripts/ops/README.md section 11):
-- ids, event type, consumer, attempts and error code only, never the payload or the aggregate id. Read-only.
-- psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/ops/outbox-dead-list.sql
SELECT id, event_id, event_type, consumer, attempts, last_error_code, occurred_at
FROM outbox_events
WHERE status = 'DEAD'
ORDER BY id;
