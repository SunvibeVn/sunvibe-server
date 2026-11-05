-- Inventory intake (property-create intake spec 5.2 file 1, section 8, D5-18, E-11): a property made from a surveyed lead
-- has no honest value for the legacy listing columns, so they become optional instead of holding a fake 0 or STUDIO.
-- Expand only. DROP NOT NULL and ADD COLUMN with no default change metadata alone: no row is rewritten or scanned. Both
-- CHECKs are added NOT VALID, so nothing here reads the populated rows, and the next migration validates them under a
-- SHARE UPDATE EXCLUSIVE lock. The ACCESS EXCLUSIVE lock of each ALTER is held to the end of this file, which does no slow
-- work; a timeout fails the start-up and the runbook retries.
-- A running older binary keeps working: it fills every legacy column and none of the new ones, which is exactly what the
-- first CHECK asks of a row with no intake. Never re-add NOT NULL once an intake row exists (spec 7.4).
-- intake_id and ward_code are plain ids: an FK from properties would block its writers until the file ends (spec 5.2).
SET LOCAL lock_timeout = '5s';

ALTER TABLE properties
    ALTER COLUMN status DROP NOT NULL,
    ALTER COLUMN monthly_price DROP NOT NULL,
    ALTER COLUMN deposit_price DROP NOT NULL,
    ALTER COLUMN min_rent_months DROP NOT NULL,
    ALTER COLUMN property_type DROP NOT NULL,
    ALTER COLUMN bedrooms DROP NOT NULL,
    ALTER COLUMN bathrooms DROP NOT NULL,
    ALTER COLUMN square_meters DROP NOT NULL,
    ALTER COLUMN district DROP NOT NULL,
    ADD COLUMN asset_type VARCHAR(20),
    ADD COLUMN rental_layout VARCHAR(10),
    ADD COLUMN intake_id VARCHAR(26),
    ADD COLUMN ward_code VARCHAR(10),
    ADD COLUMN address_norm VARCHAR(255),
    ADD CONSTRAINT chk_properties_legacy_columns_present CHECK (intake_id IS NOT NULL OR (
        status IS NOT NULL AND monthly_price IS NOT NULL AND deposit_price IS NOT NULL
        AND min_rent_months IS NOT NULL AND property_type IS NOT NULL AND bedrooms IS NOT NULL
        AND bathrooms IS NOT NULL AND square_meters IS NOT NULL AND district IS NOT NULL)) NOT VALID,
    ADD CONSTRAINT chk_properties_intake_columns_present CHECK (intake_id IS NULL OR (
        asset_type IS NOT NULL AND rental_layout IS NOT NULL AND ward_code IS NOT NULL)) NOT VALID;

ALTER TABLE units
    ALTER COLUMN unit_type DROP NOT NULL,
    ADD COLUMN location_label VARCHAR(250);
