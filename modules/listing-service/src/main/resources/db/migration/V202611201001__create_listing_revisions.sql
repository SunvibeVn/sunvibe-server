-- The revisions of a listing (listing-publish E06-S1 spec 5.3, E06S1-D7; listing BR-2, section 3.3). Creates a new,
-- empty table. created_by names a user of auth-service and is a plain id. The content columns arrive with the
-- revision save of a later slice. The status CHECK holds the whole vocabulary of listing section 3.3. Three partial
-- unique indexes hold the counts per listing (listing-publish BR-6, US-2 AC-6, listing section 3.3, E06S1-D7): at most
-- one DRAFT or PENDING_REVIEW, at most one APPROVED, at most one LIVE; a later command that moves one revision out and
-- another in updates the outgoing row first. uq_listing_revisions_number also serves the foreign key and the read of
-- the newest revisions (E06S1-D17).
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_revisions (
    id VARCHAR(26) NOT NULL,
    listing_id VARCHAR(26) NOT NULL,
    revision_no INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT listing_revisions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_listing_revisions_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT ck_listing_revisions_number CHECK (revision_no > 0),
    CONSTRAINT ck_listing_revisions_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'NEEDS_CHANGES', 'REJECTED',
        'APPROVED', 'LIVE', 'REPLACED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_listing_revisions_number ON listing_revisions (listing_id, revision_no);
CREATE UNIQUE INDEX uq_listing_revisions_open ON listing_revisions (listing_id)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW');
CREATE UNIQUE INDEX uq_listing_revisions_approved ON listing_revisions (listing_id) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX uq_listing_revisions_live ON listing_revisions (listing_id) WHERE status = 'LIVE';
