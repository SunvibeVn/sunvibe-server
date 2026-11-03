-- Validates the four CHECKs widened NOT VALID by the two previous migrations (survey-media spec 5.2 file 5), after
-- which no survey_ constraint is left unvalidated. VALIDATE scans the populated rows under a SHARE UPDATE EXCLUSIVE
-- lock, so reads and writes go on; its time was measured on a production-size copy (survey-media plan PD-8). Every old
-- row passes: each widened list is an exact superset of the old one.
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_security_events VALIDATE CONSTRAINT chk_survey_security_events_resource_type;
ALTER TABLE survey_security_events VALIDATE CONSTRAINT chk_survey_security_events_action;
ALTER TABLE survey_security_events VALIDATE CONSTRAINT chk_survey_security_events_outcome;
ALTER TABLE survey_access_logs VALIDATE CONSTRAINT chk_survey_access_logs_resource_type;
