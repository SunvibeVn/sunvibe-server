-- Validates the CHECK V202611211001 added NOT VALID (listing-publish E06-S3 spec 5.3 item 3; flyway.md "Locks and
-- populated tables"). VALIDATE CONSTRAINT takes SHARE UPDATE EXCLUSIVE: reads and writes go on while it scans. It cannot
-- fail on staging: V202611211001 added both columns without a default, so every existing row holds NULL and NULL, which
-- the CHECK admits, and nothing writes them before this slice's code serves. Were it to fail, this file rolls back and
-- the start of the new instance stops. Forward fix: expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listings VALIDATE CONSTRAINT ck_listings_freshness;
