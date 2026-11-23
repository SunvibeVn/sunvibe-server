-- The time a revision went live and the time it was replaced (listing-publish E06-S6 spec E06S6-D6 "Times"; US-5 AC-4,
-- BR-6): a Live or Replaced revision has went_live_at, only a Replaced one has replaced_at, no other status has either.
-- The domain sets them only when a revision goes live or is replaced; these two CHECKs are the only guard of the rule
-- on rows.
-- Cost (flyway.md "Locks and populated tables"): listing_revisions holds rows on staging. Two nullable columns without
-- a default change only metadata, no rewrite. The two CHECKs are added NOT VALID, so this file scans no row; the next
-- file validates them under SHARE UPDATE EXCLUSIVE. No row on staging is Live or Replaced (no command wrote either
-- status before this slice), so every existing row has both columns null and passes. Forward fix only: a fix is a new
-- file.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listing_revisions ADD COLUMN went_live_at TIMESTAMPTZ, ADD COLUMN replaced_at TIMESTAMPTZ;

ALTER TABLE listing_revisions ADD CONSTRAINT ck_listing_revisions_went_live_at
    CHECK ((status IN ('LIVE', 'REPLACED')) = (went_live_at IS NOT NULL)) NOT VALID;

ALTER TABLE listing_revisions ADD CONSTRAINT ck_listing_revisions_replaced_at
    CHECK ((status = 'REPLACED') = (replaced_at IS NOT NULL)) NOT VALID;
