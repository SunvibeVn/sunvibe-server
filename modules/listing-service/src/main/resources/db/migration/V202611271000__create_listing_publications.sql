-- The publication of a listing (listing-publish E06-S7 spec 5.3, E06S7-D2 to D4; ADR-012 decisions 1, 9; listing BR-6,
-- listing-publish BR-14): one row per listing whose live revision may be shown, the flat copy of that revision's public
-- data that the public read serves. Written only by the put live, in its guarded write; read only by the public read and
-- the signal of a unit's listings. Public columns, exactly BR-14's groups of this slice: code, the transaction type, the
-- unit snapshot's type, place codes, rooms and area, the terms snapshot (its fees are rows of
-- listing_publication_fees), title, description, the thirteen features with the names and types of listing_revisions,
-- and the put-live time. Internal columns, exactly: listing_id, unit_id, agreement_id (plain ids: unit and agreement name
-- rows of other modules), terms_digest (SHA-256 of the terms' canonical string, 64 lowercase hex) and row_version. No
-- jsonb or free-form column; types and CHECKs mirror V202611201100__add_listing_revision_content.sql. No row is copied
-- (E06S7-D4): a listing put live before this slice answers the public 404 until a revision goes live after it.
-- A new, empty table: every constraint is created valid; its foreign key takes a SHARE ROW EXCLUSIVE lock on listings for
-- this file's short transaction, bounded by lock_timeout. The primary key indexes the foreign key. Forward fix:
-- expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_publications (
    listing_id VARCHAR(26) NOT NULL,
    code VARCHAR(12) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    property_type VARCHAR(20),
    province_code VARCHAR(10),
    ward_code VARCHAR(10),
    bedrooms INTEGER NOT NULL,
    bathrooms INTEGER NOT NULL,
    area_sqm DOUBLE PRECISION NOT NULL,
    monthly_rent NUMERIC(15, 0) NOT NULL,
    deposit_basis VARCHAR(6) NOT NULL,
    deposit_months INTEGER,
    deposit_amount NUMERIC(15, 0),
    minimum_term_months INTEGER NOT NULL,
    payment_period_months INTEGER NOT NULL,
    title VARCHAR(90) NOT NULL,
    description VARCHAR(3000) NOT NULL,
    feature_furnishing VARCHAR(5),
    feature_air_conditioner BOOLEAN NOT NULL,
    feature_water_heater BOOLEAN NOT NULL,
    feature_fridge BOOLEAN NOT NULL,
    feature_washing_machine VARCHAR(7),
    feature_kitchen VARCHAR(7),
    feature_balcony BOOLEAN NOT NULL,
    feature_elevator BOOLEAN NOT NULL,
    feature_motorbike_parking BOOLEAN NOT NULL,
    feature_car_parking BOOLEAN NOT NULL,
    feature_internet BOOLEAN NOT NULL,
    feature_pets VARCHAR(11),
    feature_no_shared_landlord BOOLEAN NOT NULL,
    went_live_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unit_id VARCHAR(26) NOT NULL,
    agreement_id VARCHAR(26) NOT NULL,
    terms_digest VARCHAR(64) NOT NULL,
    row_version BIGINT NOT NULL,
    CONSTRAINT listing_publications_pkey PRIMARY KEY (listing_id),
    CONSTRAINT fk_listing_publications_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_publications_transaction_type CHECK (transaction_type = 'LONG_TERM_RENT'),
    CONSTRAINT ck_listing_publications_property_type
        CHECK (property_type IS NULL OR property_type IN ('HOUSE', 'WHOLE_BUILDING', 'VILLA', 'HOMESTAY', 'CONDOTEL',
            'APARTMENT')),
    -- The ward and its province come together from property's catalog, or not at all (E06S2-D12).
    CONSTRAINT ck_listing_publications_place
        CHECK ((province_code IS NULL AND ward_code IS NULL) OR (province_code IS NOT NULL AND ward_code IS NOT NULL)),
    -- E05's bounds: a rent of 1 to 999,999,999,999,999 VND, a minimum term of 1 to 120 months, a payment period of 1 to
    -- 12 months.
    CONSTRAINT ck_listing_publications_terms
        CHECK (monthly_rent BETWEEN 1 AND 999999999999999 AND minimum_term_months BETWEEN 1 AND 120
            AND payment_period_months BETWEEN 1 AND 12),
    -- E05's deposit shape: months of rent without an amount, or an amount without months; 0 is no deposit.
    CONSTRAINT ck_listing_publications_deposit
        CHECK ((deposit_basis = 'MONTHS' AND deposit_months IS NOT NULL AND deposit_months BETWEEN 0 AND 12
                AND deposit_amount IS NULL)
            OR (deposit_basis = 'AMOUNT' AND deposit_amount IS NOT NULL
                AND deposit_amount BETWEEN 0 AND 999999999999999 AND deposit_months IS NULL)),
    CONSTRAINT ck_listing_publications_feature_furnishing
        CHECK (feature_furnishing IS NULL OR feature_furnishing IN ('FULL', 'BASIC', 'NONE')),
    CONSTRAINT ck_listing_publications_feature_washing_machine
        CHECK (feature_washing_machine IS NULL OR feature_washing_machine IN ('PRIVATE', 'SHARED')),
    CONSTRAINT ck_listing_publications_feature_kitchen
        CHECK (feature_kitchen IS NULL OR feature_kitchen IN ('PRIVATE', 'SHARED')),
    CONSTRAINT ck_listing_publications_feature_pets
        CHECK (feature_pets IS NULL OR feature_pets IN ('ALLOWED', 'NOT_ALLOWED')),
    CONSTRAINT ck_listing_publications_terms_digest CHECK (char_length(terms_digest) = 64),
    CONSTRAINT ck_listing_publications_row_version CHECK (row_version >= 0)
);

-- listing-detail BR-15: the public read finds a row by its code, which no other listing holds.
CREATE UNIQUE INDEX uq_listing_publications_code ON listing_publications (code);
-- E06S7-D13: the listings of a unit that have a publication row, for the signal of E05's event.
CREATE INDEX ix_listing_publications_unit ON listing_publications (unit_id);

COMMENT ON TABLE listing_publications IS
    'The public data of each listing''s live revision, written only by the put live (listing-publish E06-S7, ADR-012 '
    'decision 1). Public columns are listing-publish BR-14''s; listing_id, unit_id, agreement_id, terms_digest and '
    'row_version are internal and never in an answer.';
