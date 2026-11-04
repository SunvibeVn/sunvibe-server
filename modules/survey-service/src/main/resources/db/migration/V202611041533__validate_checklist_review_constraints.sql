-- Validates the constraints the previous migration added NOT VALID (survey-checklist submission spec 5.3), after which
-- no survey_ constraint is left unvalidated. VALIDATE scans the populated rows under a SHARE UPDATE EXCLUSIVE lock, so
-- reads and writes go on. Every old row passes: each widened list is an exact superset of the old one, every old
-- checklist is a DRAFT without a submission or a decision, and no old row has a previous checklist.
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_checklists VALIDATE CONSTRAINT chk_survey_checklists_status;
ALTER TABLE survey_checklists VALIDATE CONSTRAINT fk_survey_checklists_previous_checklist_id;
ALTER TABLE survey_checklists VALIDATE CONSTRAINT chk_survey_checklists_submitted_pair;
ALTER TABLE survey_checklists VALIDATE CONSTRAINT chk_survey_checklists_submitted_status;
ALTER TABLE survey_checklists VALIDATE CONSTRAINT chk_survey_checklists_decided_status;
ALTER TABLE survey_checklist_takeovers VALIDATE CONSTRAINT chk_survey_checklist_takeovers_kind;
ALTER TABLE survey_security_events VALIDATE CONSTRAINT chk_survey_security_events_action;
ALTER TABLE survey_security_events VALIDATE CONSTRAINT chk_survey_security_events_outcome;
