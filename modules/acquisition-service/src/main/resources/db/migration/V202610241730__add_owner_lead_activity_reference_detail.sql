-- The checklist group of a survey checklist History entry (survey-checklist spec 5.2; survey-checklist brief BR-15).
-- Expand only: one nullable column and one CHECK; no row is rewritten and no existing column or constraint changes,
-- so the previous server version keeps running on the migrated table. Existing rows keep reference_detail null.
-- owner_lead_activities.reference_id/reference_no now name a survey appointment or a survey checklist, by activity
-- type; reference_detail is the group (IDENTIFICATION, PARTS, ...) of a CHECKLIST_SAVED entry and needs the
-- reference. No text is ever stored here (survey-checklist BR-15, survey BR-14).
-- Repeatable: IF NOT EXISTS and DROP CONSTRAINT IF EXISTS make a second run change nothing.
ALTER TABLE owner_lead_activities ADD COLUMN IF NOT EXISTS reference_detail VARCHAR(30);
ALTER TABLE owner_lead_activities DROP CONSTRAINT IF EXISTS chk_owner_lead_activities_reference_detail;
ALTER TABLE owner_lead_activities ADD CONSTRAINT chk_owner_lead_activities_reference_detail
    CHECK (reference_detail IS NULL OR reference_id IS NOT NULL);
