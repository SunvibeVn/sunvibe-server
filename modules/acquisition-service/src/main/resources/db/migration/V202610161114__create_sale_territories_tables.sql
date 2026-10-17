-- Who looks after which ward (acquisition-owner-lead-queue BR-5): a ward has at most one person, a person can have
-- many wards, so the ward code is the primary key. Changes are recorded in a history table
-- (acquisition-owner-lead-queue BR-10), insert-only.
-- Expand only: new tables, no existing table, column or row changes; no FK to other modules. ward_code has no FK to
-- the property-service catalog and user_id, assigned_by and the actor columns have none to users (ADR-009).
-- id of the history is a Snowflake id (BIGINT). old_user_id NULL means the ward had nobody, new_user_id NULL
-- means the ward was left unassigned.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS sale_territories (
    ward_code VARCHAR(10) NOT NULL,
    user_id VARCHAR(26) NOT NULL,
    assigned_by VARCHAR(26) NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT sale_territories_pkey PRIMARY KEY (ward_code)
);

CREATE INDEX IF NOT EXISTS idx_sale_territories_user_id ON sale_territories (user_id);

CREATE TABLE IF NOT EXISTS sale_territory_changes (
    id BIGINT NOT NULL,
    ward_code VARCHAR(10) NOT NULL,
    old_user_id VARCHAR(26),
    new_user_id VARCHAR(26),
    actor_user_id VARCHAR(26) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT sale_territory_changes_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_sale_territory_changes_ward_code ON sale_territory_changes (ward_code);
