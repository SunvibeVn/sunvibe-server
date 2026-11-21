-- The freshness records of a listing (listing-publish E06-S3 spec 5.3 item 1, E06S3-D4, E06S3-D6, E06S3-D9, E06S3-D10;
-- US-6 AC-1, BR-9, BR-11, BR-17): one row per contact of the assigned Sale with the person letting the unit. Creates a
-- new, empty table. Insert-only. A record is part of its listing, so it goes with it (ON DELETE CASCADE): the E2E lead
-- seed and the test cleanups that delete listings need no edit. recorded_by names a user of auth-service and is a plain
-- id. The two answers of BR-9 are present exactly for "still available"; valid is stored and held to its rule by a
-- CHECK. listing_row_version is the listing's row version the record moved it to: uq_listing_freshness_records_version
-- keeps it unique per listing (a backstop behind the guarded UPDATE of the listing row, E06S3-P14), finds the latest
-- record and indexes the foreign key. note is private free text (BR-17, retention SV-117): never exported, never in a
-- log or an event. The foreign key takes a SHARE ROW EXCLUSIVE lock on listings for this file's short transaction,
-- bounded by lock_timeout. Forward fix: expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_freshness_records (
    id VARCHAR(26) NOT NULL,
    listing_id VARCHAR(26) NOT NULL,
    listing_row_version BIGINT NOT NULL,
    contact_date DATE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by VARCHAR(26) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    terms_still_apply BOOLEAN,
    unit_unchanged BOOLEAN,
    valid BOOLEAN NOT NULL,
    note VARCHAR(500),
    CONSTRAINT listing_freshness_records_pkey PRIMARY KEY (id),
    CONSTRAINT fk_listing_freshness_records_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_freshness_records_channel CHECK (channel IN ('CALL', 'MESSAGE', 'IN_PERSON')),
    CONSTRAINT ck_listing_freshness_records_outcome CHECK (outcome IN ('AVAILABLE', 'NOT_AVAILABLE', 'UNREACHABLE')),
    -- listing-publish BR-9: both answers for "still available", none for another outcome.
    CONSTRAINT ck_listing_freshness_records_answers CHECK (
        (outcome = 'AVAILABLE' AND terms_still_apply IS NOT NULL AND unit_unchanged IS NOT NULL)
        OR (outcome <> 'AVAILABLE' AND terms_still_apply IS NULL AND unit_unchanged IS NULL)),
    -- listing-publish BR-9, BR-11: a valid confirmation is "still available" with both answers yes.
    CONSTRAINT ck_listing_freshness_records_valid CHECK (
        valid = (outcome = 'AVAILABLE' AND terms_still_apply IS TRUE AND unit_unchanged IS TRUE)),
    CONSTRAINT ck_listing_freshness_records_row_version CHECK (listing_row_version >= 1)
);

CREATE UNIQUE INDEX uq_listing_freshness_records_version ON listing_freshness_records (listing_id, listing_row_version);

COMMENT ON TABLE listing_freshness_records IS
    'Contacts of the assigned Sale with the person letting a listing''s unit (listing-publish US-6, BR-9). note is '
    'private (BR-17): never exported, logged or sent in an event.';
