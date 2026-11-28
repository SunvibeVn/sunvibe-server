-- The mandatory fees of a listing's publication (listing-publish E06-S7 spec 5.3, E06S7-D2; BR-14 group 6): one row per
-- fee of the live revision's terms snapshot, numbered 1 to 20 in E05's order, the rules of listing_revision_fees
-- (V202611201101__create_listing_revision_fees.sql). A fee row is part of its publication, so it goes with it (ON DELETE
-- CASCADE), and the publication goes with its listing. The primary key also indexes the foreign key. A new, empty table:
-- every constraint is created valid; its foreign key takes a SHARE ROW EXCLUSIVE lock on listing_publications for this
-- file's short transaction, bounded by lock_timeout. Forward fix: expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_publication_fees (
    listing_id VARCHAR(26) NOT NULL,
    fee_no INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    basis VARCHAR(20) NOT NULL,
    amount NUMERIC(15, 0),
    charge_unit VARCHAR(30),
    CONSTRAINT pk_listing_publication_fees PRIMARY KEY (listing_id, fee_no),
    CONSTRAINT fk_listing_publication_fees_publication FOREIGN KEY (listing_id)
        REFERENCES listing_publications (listing_id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_publication_fees_fee_no CHECK (fee_no BETWEEN 1 AND 20),
    CONSTRAINT ck_listing_publication_fees_basis CHECK (basis IN ('AMOUNT', 'INCLUDED', 'PROVIDER_TARIFF')),
    CONSTRAINT ck_listing_publication_fees_charge_unit CHECK (charge_unit IS NULL OR charge_unit IN ('PER_MONTH',
        'PER_KWH', 'PER_M3', 'PER_PERSON_PER_MONTH', 'PER_VEHICLE_PER_MONTH', 'ONE_OFF')),
    -- A CHECK passes when it yields NULL, so the amount is tested IS NOT NULL before its range.
    CONSTRAINT ck_listing_publication_fees_amount CHECK (
        (basis = 'AMOUNT' AND amount IS NOT NULL AND amount BETWEEN 1 AND 999999999999999
            AND charge_unit IS NOT NULL)
        OR (basis <> 'AMOUNT' AND amount IS NULL AND charge_unit IS NULL)),
    CONSTRAINT ck_listing_publication_fees_name CHECK (char_length(name) BETWEEN 1 AND 100)
);

COMMENT ON TABLE listing_publication_fees IS
    'Mandatory fees of a listing''s publication, copied from its live revision (listing-publish E06-S7 BR-14 group 6).';
