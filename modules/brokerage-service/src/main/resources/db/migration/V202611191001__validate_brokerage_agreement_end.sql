-- Validates the two CHECKs added NOT VALID by the previous file (brokerage-agreement E05-S8 spec S8-D9; flyway.md, "A new
-- CHECK ... on existing rows").
-- Cost on a populated table: VALIDATE CONSTRAINT takes a SHARE UPDATE EXCLUSIVE lock on brokerage_agreements, so reads and
-- row writes go on while it scans; a separate file, so the scan runs without the previous file's ACCESS EXCLUSIVE lock.
-- The table holds only the E05 agreements on staging, none in a final state written by a command and all NULL in the new
-- columns, and no row in production, so the scan takes milliseconds, bounded by lock_timeout.
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreements VALIDATE CONSTRAINT ck_brokerage_agreements_end;
ALTER TABLE brokerage_agreements VALIDATE CONSTRAINT ck_brokerage_agreements_termination;
