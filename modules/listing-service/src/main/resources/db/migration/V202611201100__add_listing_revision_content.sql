-- The content of a listing revision (listing-publish E06-S2 spec 5.3, E06S2-D4 to D7, E06S2-D12, E06S2-D15; BR-2 to
-- BR-4; US-2 AC-1 to AC-5): title and description, the thirteen features as typed columns, the terms snapshot copied by
-- value from E05 (its fees are rows of listing_revision_fees), the unit snapshot of the submission, and the submission.
-- Amounts are whole VND (ADR-006); the bounds are E05's own CHECKs. submitted_by names a user of auth-service and is a
-- plain id; no foreign key to another module.
-- Cost on a populated table (flyway.md): listing_revisions holds E06-S1's contentless drafts on staging. Every column is
-- nullable without a default, or a BOOLEAN with the constant default FALSE: metadata only, no rewrite. Each CHECK is
-- added NOT VALID, so this file reads no row; V202611201102 validates them under SHARE UPDATE EXCLUSIVE. Each CHECK tests
-- its columns IS NOT NULL before comparing them (a CHECK passes on NULL). Forward fix (E06S2-D15): this file is
-- expand-only; a wrong column or CHECK is fixed by a new file (ALTER, or DROP and ADD of a constraint NOT VALID with its
-- validation), never by editing this one or dropping a column without the owner.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listing_revisions
    ADD COLUMN title VARCHAR(90),
    ADD COLUMN description VARCHAR(3000),
    ADD COLUMN feature_furnishing VARCHAR(5),
    ADD COLUMN feature_air_conditioner BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_water_heater BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_fridge BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_washing_machine VARCHAR(7),
    ADD COLUMN feature_kitchen VARCHAR(7),
    ADD COLUMN feature_balcony BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_elevator BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_motorbike_parking BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_car_parking BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_internet BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_pets VARCHAR(11),
    ADD COLUMN feature_no_shared_landlord BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN terms_source VARCHAR(13),
    ADD COLUMN terms_pending_start_date DATE,
    ADD COLUMN terms_captured_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN terms_monthly_rent NUMERIC(15, 0),
    ADD COLUMN terms_deposit_basis VARCHAR(6),
    ADD COLUMN terms_deposit_months INTEGER,
    ADD COLUMN terms_deposit_amount NUMERIC(15, 0),
    ADD COLUMN terms_minimum_term_months INTEGER,
    ADD COLUMN terms_payment_period_months INTEGER,
    ADD COLUMN unit_asset_type VARCHAR(20),
    ADD COLUMN unit_province_code VARCHAR(10),
    ADD COLUMN unit_province_name VARCHAR(100),
    ADD COLUMN unit_ward_code VARCHAR(10),
    ADD COLUMN unit_ward_name VARCHAR(100),
    ADD COLUMN unit_bedrooms INTEGER,
    ADD COLUMN unit_bathrooms INTEGER,
    ADD COLUMN unit_area_sqm DOUBLE PRECISION,
    ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN submitted_by VARCHAR(26);

ALTER TABLE listing_revisions
    ADD CONSTRAINT ck_listing_revisions_feature_furnishing
        CHECK (feature_furnishing IS NULL OR feature_furnishing IN ('FULL', 'BASIC', 'NONE')) NOT VALID,
    ADD CONSTRAINT ck_listing_revisions_feature_washing_machine
        CHECK (feature_washing_machine IS NULL OR feature_washing_machine IN ('PRIVATE', 'SHARED')) NOT VALID,
    ADD CONSTRAINT ck_listing_revisions_feature_kitchen
        CHECK (feature_kitchen IS NULL OR feature_kitchen IN ('PRIVATE', 'SHARED')) NOT VALID,
    ADD CONSTRAINT ck_listing_revisions_feature_pets
        CHECK (feature_pets IS NULL OR feature_pets IN ('ALLOWED', 'NOT_ALLOWED')) NOT VALID,
    ADD CONSTRAINT ck_listing_revisions_terms_source
        CHECK (terms_source IS NULL OR terms_source IN ('IN_EFFECT', 'PENDING_START')) NOT VALID,
    -- No terms at all, or complete terms: the time taken, a rent of 1 to 999,999,999,999,999 VND, a deposit basis, a
    -- minimum term of 1 to 120 months and a payment period of 1 to 12 months; the pending start date exactly for a
    -- snapshot of the version waiting to start.
    ADD CONSTRAINT ck_listing_revisions_terms_shape
        CHECK ((terms_source IS NULL AND terms_pending_start_date IS NULL AND terms_captured_at IS NULL
                AND terms_monthly_rent IS NULL AND terms_deposit_basis IS NULL AND terms_deposit_months IS NULL
                AND terms_deposit_amount IS NULL AND terms_minimum_term_months IS NULL
                AND terms_payment_period_months IS NULL)
            OR (terms_source IS NOT NULL AND terms_captured_at IS NOT NULL
                AND terms_monthly_rent IS NOT NULL AND terms_monthly_rent BETWEEN 1 AND 999999999999999
                AND terms_deposit_basis IS NOT NULL
                AND terms_minimum_term_months IS NOT NULL AND terms_minimum_term_months BETWEEN 1 AND 120
                AND terms_payment_period_months IS NOT NULL AND terms_payment_period_months BETWEEN 1 AND 12
                AND (terms_source = 'PENDING_START') = (terms_pending_start_date IS NOT NULL))) NOT VALID,
    -- E05's deposit shape (ck_brokerage_agreement_versions_deposit): months of rent without an amount, or an amount
    -- without months; 0 is no deposit.
    ADD CONSTRAINT ck_listing_revisions_terms_deposit
        CHECK ((terms_deposit_basis IS NULL AND terms_deposit_months IS NULL AND terms_deposit_amount IS NULL)
            OR (terms_deposit_basis IS NOT NULL AND terms_deposit_basis = 'MONTHS' AND terms_deposit_months IS NOT NULL
                AND terms_deposit_months BETWEEN 0 AND 12 AND terms_deposit_amount IS NULL)
            OR (terms_deposit_basis IS NOT NULL AND terms_deposit_basis = 'AMOUNT' AND terms_deposit_amount IS NOT NULL
                AND terms_deposit_amount BETWEEN 0 AND 999999999999999 AND terms_deposit_months IS NULL)) NOT VALID,
    -- The ward and its province come together from property's catalog, or not at all (E06S2-D12).
    ADD CONSTRAINT ck_listing_revisions_unit_place
        CHECK ((unit_province_code IS NULL AND unit_province_name IS NULL AND unit_ward_code IS NULL
                AND unit_ward_name IS NULL)
            OR (unit_province_code IS NOT NULL AND unit_province_name IS NOT NULL AND unit_ward_code IS NOT NULL
                AND unit_ward_name IS NOT NULL)) NOT VALID,
    ADD CONSTRAINT ck_listing_revisions_unit_asset_type
        CHECK (unit_asset_type IS NULL OR unit_asset_type IN ('HOUSE', 'WHOLE_BUILDING', 'VILLA', 'HOMESTAY',
            'CONDOTEL', 'APARTMENT')) NOT VALID,
    ADD CONSTRAINT ck_listing_revisions_submission
        CHECK ((submitted_at IS NULL AND submitted_by IS NULL)
            OR (submitted_at IS NOT NULL AND submitted_by IS NOT NULL)) NOT VALID,
    -- listing-publish US-2 AC-4, AC-5: a Pending review revision holds its title, description, terms, unit snapshot
    -- and submission.
    ADD CONSTRAINT ck_listing_revisions_pending_content
        CHECK (status <> 'PENDING_REVIEW'
            OR (title IS NOT NULL AND description IS NOT NULL AND terms_source IS NOT NULL
                AND unit_bedrooms IS NOT NULL AND unit_bathrooms IS NOT NULL AND unit_area_sqm IS NOT NULL
                AND submitted_at IS NOT NULL)) NOT VALID;
