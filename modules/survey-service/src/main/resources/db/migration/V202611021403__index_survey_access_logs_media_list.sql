-- The media list grouping of survey access-log rows (survey-media spec 5.2 file 4, A-6): one MEDIA_LIST row per user
-- and checklist per 10 minutes, found through this partial index; the query repeats its predicate as a literal.
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on the populated table for the whole build (writes wait),
-- so its time was measured on a production-size copy (survey-media plan PD-8, Task A1 Step 4).
SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS idx_survey_access_logs_media_list ON survey_access_logs (user_id, resource_id, accessed_at)
    WHERE resource_type = 'MEDIA_LIST';
