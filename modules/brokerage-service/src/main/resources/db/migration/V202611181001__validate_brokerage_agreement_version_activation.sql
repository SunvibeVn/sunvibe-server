-- Validates the activation CHECK added NOT VALID by the previous file (brokerage-agreement E05-S6 spec S6-D14;
-- flyway.md, "A new CHECK ... on existing rows").
-- Cost on a populated table: VALIDATE CONSTRAINT takes a SHARE UPDATE EXCLUSIVE lock, so reads and writes go on while it
-- scans; the table holds only the E05 drafts and sent versions on staging (all NULL in the new columns) and no row in
-- production, so the scan takes milliseconds, bounded by lock_timeout.
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreement_versions VALIDATE CONSTRAINT ck_brokerage_agreement_versions_activation;
