-- Validates the constraint the previous migration added NOT VALID (property-create requalification spec 5.2). VALIDATE
-- scans the populated rows under a SHARE UPDATE EXCLUSIVE lock, so reads and writes go on. Every old row passes: the
-- column was added with no value.
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_checklists VALIDATE CONSTRAINT chk_survey_checklists_requalified_status;
