-- Media reads and the server request id on survey access-log rows (survey-media spec 4.9, 5.2, A-6, A-23).
-- Expand only, on a populated table: a new nullable column (old rows keep NULL) and the resource-type CHECK widened to
-- an exact superset of its old list under the same name; rows without a resource (survey-block reads before the
-- checklist) stay allowed, and chk_survey_access_logs_resource_pair is untouched. NOT VALID adds the CHECK without
-- scanning the rows; the VALIDATE migration checks it later under a SHARE UPDATE EXCLUSIVE lock. The previous server
-- version keeps inserting its old values and column lists (spec 7.4). One statement, one short ACCESS EXCLUSIVE lock,
-- bounded by the lock timeout (a timeout fails the start-up; the runbook retries).
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_access_logs
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(26),
    DROP CONSTRAINT IF EXISTS chk_survey_access_logs_resource_type,
    ADD CONSTRAINT chk_survey_access_logs_resource_type CHECK (resource_type IS NULL OR resource_type IN (
        'SURVEY_BLOCK', 'CHECKLIST', 'CHECKPOINT', 'MEDIA_LIST', 'MEDIA_PREVIEW', 'MEDIA_FULL', 'MEDIA_DOWNLOAD',
        'MEDIA_HELD_READ')) NOT VALID;
