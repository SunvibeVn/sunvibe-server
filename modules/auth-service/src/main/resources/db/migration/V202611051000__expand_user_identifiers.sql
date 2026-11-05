-- Identity independent of a phone (auth-email-access spec 5.2 item 1, A-2, A-3, A-6, D4-12): typed login identifiers
-- with canonical keys in new nullable columns. Stored email and username values are not rewritten.
-- Expand only, on a populated table, one ACCESS EXCLUSIVE lock; the lock timeout bounds the wait for it, not how long it
-- is held (a timeout fails the start-up; the runbook retries). Cost of each step:
--   email to VARCHAR(254): binary coercible, no heap rewrite; the partial idx_users_email is rebuilt under the lock, and
--     that is the only step here that grows with the table. Its statistics are collected again by the next file.
--   username DROP NOT NULL: metadata only.
--   ADD COLUMN: metadata only (no default, or a constant one).
--   ADD CONSTRAINT ... NOT VALID: scans nothing; the next file validates it under SHARE UPDATE EXCLUSIVE.
-- A server of the previous release started after this file keeps working: it reads no new column and writes the old ones.
SET LOCAL lock_timeout = '5s';

ALTER TABLE users
    ALTER COLUMN email TYPE VARCHAR(254),
    ALTER COLUMN username DROP NOT NULL,
    ADD COLUMN email_key VARCHAR(254),
    ADD COLUMN phone_key VARCHAR(16),
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN reauth_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reauth_locked_until TIMESTAMP WITH TIME ZONE,
    -- A key can only be the folded form of the email of its own row: ASCII lower case through the C collation, U+0020
    -- removed at both ends, the same folding as Email.create
    ADD CONSTRAINT chk_users_email_key_matches_email
        CHECK (email_key IS NULL OR (email IS NOT NULL AND email_key = lower(btrim(email) COLLATE "C"))) NOT VALID;
