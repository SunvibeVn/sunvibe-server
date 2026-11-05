-- Validates the two CHECKs the previous migration added NOT VALID (property-create intake spec 5.2 file 2). VALIDATE scans
-- the populated rows under a SHARE UPDATE EXCLUSIVE lock, so reads and writes go on. Every row written before file 1 has
-- all its legacy columns, because they were NOT NULL, and no intake id.
SET LOCAL lock_timeout = '5s';

ALTER TABLE properties VALIDATE CONSTRAINT chk_properties_legacy_columns_present;
ALTER TABLE properties VALIDATE CONSTRAINT chk_properties_intake_columns_present;
