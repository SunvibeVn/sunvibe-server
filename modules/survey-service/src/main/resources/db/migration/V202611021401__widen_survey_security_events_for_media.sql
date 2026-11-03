-- Media values and the server request id on survey security events (survey-media spec 4.9, 5.2, A-19, A-23, A-26).
-- Expand only, on a populated table: a new nullable column (old rows keep NULL) and three CHECKs widened to exact
-- supersets of their old value lists, under the same names. NOT VALID adds them without scanning the rows; the
-- VALIDATE migration that follows checks them under a SHARE UPDATE EXCLUSIVE lock, so writes go on. The previous
-- server version keeps inserting its old values and column lists (spec 7.4). One statement, one short ACCESS EXCLUSIVE
-- lock, bounded by the lock timeout (a timeout fails the start-up; the runbook retries).
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_security_events
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(26),
    DROP CONSTRAINT IF EXISTS chk_survey_security_events_resource_type,
    ADD CONSTRAINT chk_survey_security_events_resource_type CHECK (resource_type IN ('LEAD', 'APPOINTMENT',
        'CHECKLIST', 'CHECKPOINT', 'MEDIA', 'MEDIA_SET')) NOT VALID,
    DROP CONSTRAINT IF EXISTS chk_survey_security_events_action,
    ADD CONSTRAINT chk_survey_security_events_action CHECK (action IN ('READ', 'START', 'SAVE', 'TAKEOVER', 'UPLOAD',
        'REMOVE', 'SCAN', 'HOLD', 'RELEASE')) NOT VALID,
    DROP CONSTRAINT IF EXISTS chk_survey_security_events_outcome,
    ADD CONSTRAINT chk_survey_security_events_outcome CHECK (outcome IN ('NOT_VISIBLE', 'NOT_ASSIGNEE',
        'MALWARE_DETECTED', 'HELD', 'RELEASED', 'BUDGET_EXCEEDED')) NOT VALID;
