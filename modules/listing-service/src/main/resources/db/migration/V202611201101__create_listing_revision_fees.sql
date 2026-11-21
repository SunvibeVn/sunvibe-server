-- The mandatory fees of a revision's terms snapshot (listing-publish E06-S2 spec 5.3, E06S2-D6, E06S2-D15; BR-4): one
-- row per fee, numbered 1 to 20 in E05's order, copied by value from E05's version; the rules of E05's own fee table
-- (V202611161001__create_brokerage_agreement_version_fees.sql). A fee row is part of its revision, so it goes with it
-- (ON DELETE CASCADE): the E2E lead seed and the test cleanups that delete revisions need no edit. The primary key also
-- indexes the foreign key. A new, empty table; its foreign key takes a SHARE ROW EXCLUSIVE lock on listing_revisions
-- for the file's short transaction, bounded by lock_timeout. Forward fix (E06S2-D15): expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_revision_fees (
    revision_id VARCHAR(26) NOT NULL,
    fee_no INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    basis VARCHAR(20) NOT NULL,
    amount NUMERIC(15, 0),
    charge_unit VARCHAR(30),
    CONSTRAINT pk_listing_revision_fees PRIMARY KEY (revision_id, fee_no),
    CONSTRAINT fk_listing_revision_fees_revision FOREIGN KEY (revision_id)
        REFERENCES listing_revisions (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_revision_fees_fee_no CHECK (fee_no BETWEEN 1 AND 20),
    CONSTRAINT ck_listing_revision_fees_basis CHECK (basis IN ('AMOUNT', 'INCLUDED', 'PROVIDER_TARIFF')),
    CONSTRAINT ck_listing_revision_fees_charge_unit CHECK (charge_unit IS NULL OR charge_unit IN ('PER_MONTH',
        'PER_KWH', 'PER_M3', 'PER_PERSON_PER_MONTH', 'PER_VEHICLE_PER_MONTH', 'ONE_OFF')),
    -- A CHECK passes when it yields NULL, so the amount is tested IS NOT NULL before its range.
    CONSTRAINT ck_listing_revision_fees_amount CHECK (
        (basis = 'AMOUNT' AND amount IS NOT NULL AND amount BETWEEN 1 AND 999999999999999
            AND charge_unit IS NOT NULL)
        OR (basis <> 'AMOUNT' AND amount IS NULL AND charge_unit IS NULL)),
    CONSTRAINT ck_listing_revision_fees_name CHECK (char_length(name) BETWEEN 1 AND 100)
);

COMMENT ON TABLE listing_revision_fees IS
    'Mandatory fees of a listing revision''s terms snapshot, copied from E05 (listing-publish BR-4). Unpublished until '
    'the revision goes live; no free text other than the fee name.';
