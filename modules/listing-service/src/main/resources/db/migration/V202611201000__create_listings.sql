-- Listings (listing-publish E06-S1 spec 5.3, E06S1-D4, E06S1-D6, E06S1-D7; listing BR-1, BR-2, section 3.2;
-- listing-detail BR-15). Creates a new, empty table. One row per listing: a stable shell bound for life to one unit, one
-- transaction type and one agreement, with its public code. unit_id, agreement_id and created_by name rows of other
-- modules and are plain ids (ADR-003, ADR-009). No free text. The state CHECK holds the whole vocabulary of listing
-- section 3.2, so later slices add transitions without touching it.
-- uq_listings_agreement_open keeps at most one listing not stored as archived per agreement: the stored form of "one
-- unarchived listing per unit and type" (listing BR-1, E06S1-D4), since a unit and type have at most one unfinished
-- agreement and a listing whose agreement ended is read as archived (ADR-012 decision 2). It decides a race of two
-- creates. uq_listings_code keeps every code ever issued unique; rows are never deleted by the application.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listings (
    id VARCHAR(26) NOT NULL,
    code VARCHAR(12) NOT NULL,
    unit_id VARCHAR(26) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    agreement_id VARCHAR(26) NOT NULL,
    state VARCHAR(30) NOT NULL,
    created_by VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT listings_pkey PRIMARY KEY (id),
    CONSTRAINT ck_listings_code CHECK (code ~ '^TD-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$'),
    CONSTRAINT ck_listings_transaction_type CHECK (transaction_type IN ('LONG_TERM_RENT')),
    CONSTRAINT ck_listings_state CHECK (state IN ('UNPUBLISHED', 'PUBLISHED', 'PAUSED', 'URGENTLY_UNPUBLISHED',
        'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_listings_code ON listings (code);
CREATE UNIQUE INDEX uq_listings_agreement_open ON listings (agreement_id) WHERE state <> 'ARCHIVED';
CREATE INDEX idx_listings_agreement_id ON listings (agreement_id, created_at);
CREATE INDEX idx_listings_unit ON listings (unit_id, transaction_type);
