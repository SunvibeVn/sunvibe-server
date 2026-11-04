-- Validates the CHECK added back NOT VALID by the previous migration. VALIDATE scans the populated rows under a SHARE
-- UPDATE EXCLUSIVE lock, so reads and writes go on. Every old row passes: the rule is the one it was written under.
SET LOCAL lock_timeout = '5s';

ALTER TABLE owner_lead_activities VALIDATE CONSTRAINT chk_owner_lead_activities_reference_detail;
