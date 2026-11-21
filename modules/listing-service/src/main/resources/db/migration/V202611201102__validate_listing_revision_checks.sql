-- Validates the eleven CHECKs V202611201100 added NOT VALID (listing-publish E06-S2 spec 5.3, E06S2-D15; flyway.md
-- "Locks and populated tables"). VALIDATE CONSTRAINT takes SHARE UPDATE EXCLUSIVE: reads and writes go on while it
-- scans. It validates only rows this slice's code writes: on staging every revision is a contentless Draft of E06-S1,
-- which every CHECK accepts; were one to fail, this file rolls back and the deploy stops before the app starts. Forward
-- fix (E06S2-D15): expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_feature_furnishing;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_feature_washing_machine;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_feature_kitchen;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_feature_pets;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_terms_source;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_terms_shape;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_terms_deposit;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_unit_place;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_unit_asset_type;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_submission;
ALTER TABLE listing_revisions VALIDATE CONSTRAINT ck_listing_revisions_pending_content;
