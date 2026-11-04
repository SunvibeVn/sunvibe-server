-- The outcome and kind of a checklist review in a History entry (survey-checklist submission spec 4.9, 5.3, plan PD-5):
-- reference_detail holds <OUTCOME>[:<KIND>], up to 31 characters (NEEDS_CHANGES:RESURVEY_REQUIRED). No text is ever
-- stored here (survey-checklist BR-15, survey BR-14). owner_lead_activities has no CHECK on its type, so the three new
-- activity types need no change.
-- Expand only, on a populated table: a wider VARCHAR rewrites no row. A column type change re-adds the CHECK on the
-- column and would scan the table under ACCESS EXCLUSIVE, so the same statement drops it and adds it back NOT VALID
-- under the same name; the next migration validates it under SHARE UPDATE EXCLUSIVE. The previous server version keeps
-- inserting (spec 7.4). One short ACCESS EXCLUSIVE lock, bounded by the lock timeout (a timeout fails the start-up; the
-- runbook retries).
SET LOCAL lock_timeout = '5s';

ALTER TABLE owner_lead_activities
    DROP CONSTRAINT IF EXISTS chk_owner_lead_activities_reference_detail,
    ALTER COLUMN reference_detail TYPE VARCHAR(40),
    ADD CONSTRAINT chk_owner_lead_activities_reference_detail
        CHECK (reference_detail IS NULL OR reference_id IS NOT NULL) NOT VALID;
