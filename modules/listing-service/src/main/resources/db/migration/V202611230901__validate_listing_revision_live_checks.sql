-- Validates the two CHECKs V202611230900 added NOT VALID (listing-publish E06-S6 spec E06S6-D6; flyway.md "Locks and
-- populated tables"). VALIDATE CONSTRAINT takes SHARE UPDATE EXCLUSIVE: reads and writes go on while it scans. Every
-- existing row is neither Live nor Replaced and has both times null, which both CHECKs accept; were one to fail, this
-- file rolls back and the deploy stops before the app starts. Forward fix only: a fix is a new file.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_went_live_at;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_replaced_at;
