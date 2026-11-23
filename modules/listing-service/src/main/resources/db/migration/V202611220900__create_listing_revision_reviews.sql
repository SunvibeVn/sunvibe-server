-- The decision of a reviewed revision (listing-publish E06-S4 spec 5.3, E06S4-D6, E06S4-D8; US-4 AC-3, BR-7): one row
-- per revision, keyed by the revision id, so a second decision cannot be stored. The table is insert-only: a row is
-- never updated, and it goes only with its revision (ON DELETE CASCADE). decided_by names a user of auth-service and is
-- a plain id; no foreign key to another module. The CHECKs hold the shape of each outcome: an approval carries its
-- checklist version and no reason, group or note; a return its reasons (codes joined by commas) and a note; a rejection
-- its group and a note. Each CHECK tests a column IS NOT NULL before comparing it (a CHECK passes on NULL).
-- Cost (flyway.md): a new, empty table, so nothing to validate; but its foreign key locks listing_revisions, which holds
-- rows on staging, in SHARE ROW EXCLUSIVE mode while it is added (E06S4-D18), hence the lock timeout. The primary key
-- serves the foreign key. Forward fix: a wrong column or CHECK is fixed by a new file, never by editing this one.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_revision_reviews (
    revision_id VARCHAR(26) NOT NULL,
    listing_id VARCHAR(26) NOT NULL,
    outcome VARCHAR(13) NOT NULL,
    checklist_version SMALLINT,
    change_reasons VARCHAR(120),
    rejection_group VARCHAR(20),
    note VARCHAR(1000),
    decided_by VARCHAR(26) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT listing_revision_reviews_pkey PRIMARY KEY (revision_id),
    CONSTRAINT fk_listing_revision_reviews_revision FOREIGN KEY (revision_id) REFERENCES listing_revisions (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_listing_revision_reviews_outcome CHECK (outcome IN ('APPROVED', 'NEEDS_CHANGES', 'REJECTED')),
    CONSTRAINT ck_listing_revision_reviews_checklist
        CHECK ((outcome = 'APPROVED') = (checklist_version IS NOT NULL)
            AND (checklist_version IS NULL OR checklist_version > 0)),
    CONSTRAINT ck_listing_revision_reviews_reasons
        CHECK ((outcome = 'NEEDS_CHANGES') = (change_reasons IS NOT NULL)
            AND (change_reasons IS NULL OR change_reasons ~
                '^(CONTENT_MISMATCH|TEXT_RULE_VIOLATION|MISSING_INFORMATION|TERMS_MISMATCH|OTHER)(,(CONTENT_MISMATCH|TEXT_RULE_VIOLATION|MISSING_INFORMATION|TERMS_MISMATCH|OTHER))*$')),
    CONSTRAINT ck_listing_revision_reviews_group
        CHECK ((outcome = 'REJECTED') = (rejection_group IS NOT NULL)
            AND (rejection_group IS NULL
                OR rejection_group IN ('SUSPECTED_FRAUD', 'WRONG_UNIT', 'SERIOUS_VIOLATION', 'OTHER'))),
    CONSTRAINT ck_listing_revision_reviews_note CHECK ((outcome = 'APPROVED') = (note IS NULL))
);
