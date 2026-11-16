-- The transactional outbox of ADR-014 (ADR-003 amendment A003-2: the one cross-cutting table app owns).
-- One row per event and consumer; codes and ids of aggregates that name no person only, no personal data.
CREATE TABLE outbox_events (
    id              BIGINT       NOT NULL,
    event_id        BIGINT       NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   INTEGER      NOT NULL,
    consumer        VARCHAR(100) NOT NULL,
    source_module   VARCHAR(40)  NOT NULL,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    correlation_id  VARCHAR(64),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    lease_token     VARCHAR(36),
    published_at    TIMESTAMPTZ,
    last_error_code VARCHAR(40),
    CONSTRAINT outbox_events_pkey PRIMARY KEY (id),
    CONSTRAINT outbox_events_event_consumer_key UNIQUE (event_id, consumer),
    CONSTRAINT outbox_events_event_version_check CHECK (event_version BETWEEN 1 AND 99),
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'DONE', 'DEAD')),
    CONSTRAINT outbox_events_attempts_check CHECK (attempts >= 0)
);
CREATE INDEX outbox_events_pending_due_idx ON outbox_events (next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX outbox_events_pending_consumer_due_idx ON outbox_events (consumer, next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX outbox_events_done_published_idx ON outbox_events (published_at) WHERE status = 'DONE';
CREATE INDEX outbox_events_dead_idx ON outbox_events (id) WHERE status = 'DEAD';
