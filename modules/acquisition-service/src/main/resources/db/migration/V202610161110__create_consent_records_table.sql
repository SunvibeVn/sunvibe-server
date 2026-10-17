-- Consent given by an owner when sending the intake form (acquisition-owner-intake brief BR-9): which notice
-- version was accepted, for which purpose, through which channel and when. ip_hash and user_agent_hash are HMAC
-- hashes, never the raw values (acquisition module brief BR-2: contact data is private). Each lead points to its
-- own record.
-- Expand only: new table, no existing table, column or row changes; no FK to other modules.
-- id is a ULID (ADR-006). Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS consent_records (
    id VARCHAR(26) NOT NULL,
    notice_version VARCHAR(40) NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    ip_hash VARCHAR(64),
    user_agent_hash VARCHAR(64),
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT consent_records_pkey PRIMARY KEY (id)
);
