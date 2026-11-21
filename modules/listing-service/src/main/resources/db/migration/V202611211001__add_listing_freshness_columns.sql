-- The freshness summary of a listing (listing-publish E06-S3 spec 5.3 item 2, E06S3-D6; BR-10, BR-11, listing BR-11
-- KT-3): the Vietnam date of the latest valid confirmation and whether the latest record is valid. Both are written only
-- by the freshness record command, in the guarded UPDATE that moves the row version; ListingEntity does not map them.
-- Cost on a populated table (flyway.md): listings holds the E2E listings on staging. Both columns are nullable without a
-- default: metadata only, no rewrite, and every existing row reads NULL and NULL. ck_listings_freshness admits no record
-- (both NULL), records none of them valid (no date, FALSE) or a valid confirmation with a known latest record (a date
-- and a flag), never "latest valid" without a date; it is added NOT VALID, so this file reads no row, and
-- V202611211002 validates it under SHARE UPDATE EXCLUSIVE. Forward fix: expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listings
    ADD COLUMN freshness_confirmed_on DATE,
    ADD COLUMN freshness_latest_valid BOOLEAN;

ALTER TABLE listings
    ADD CONSTRAINT ck_listings_freshness
        CHECK ((freshness_confirmed_on IS NULL AND freshness_latest_valid IS NOT TRUE)
            OR (freshness_confirmed_on IS NOT NULL AND freshness_latest_valid IS NOT NULL)) NOT VALID;
