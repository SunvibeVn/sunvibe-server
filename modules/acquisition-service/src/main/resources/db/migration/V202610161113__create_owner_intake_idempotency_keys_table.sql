-- Idempotency keys of the public owner intake (acquisition-owner-intake brief BR-10): sending the same key with the
-- same request body again returns the first lead instead of creating a second one. request_hash is the SHA-256 of
-- the normalized body in hex (64 characters), so a key reused with a different body can be refused.
-- Expand only: new table, no existing table, column or row changes; no FK to other modules.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS owner_intake_idempotency_keys (
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT owner_intake_idempotency_keys_pkey PRIMARY KEY (idempotency_key),
    CONSTRAINT fk_owner_intake_idempotency_keys_lead_id FOREIGN KEY (lead_id) REFERENCES owner_leads (id)
);

CREATE INDEX IF NOT EXISTS idx_owner_intake_idempotency_keys_lead_id ON owner_intake_idempotency_keys (lead_id);
