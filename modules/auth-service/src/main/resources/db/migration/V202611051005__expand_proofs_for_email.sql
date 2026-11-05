-- Proofs of the email flows (auth-email-access spec 5.2 item 3, 4.7, A-9): a bind code is a verification code with a
-- purpose, bound to a credential version; a reset token is bound to one too. Rows from before the release keep NULL,
-- which the application reads as invalid.
-- Expand only, on populated tables, one short ACCESS EXCLUSIVE lock per table, bounded by the lock timeout. Cost:
--   destination to VARCHAR(254): binary coercible, no heap rewrite, the column has no index yet; the statistics come
--     back in the file that indexes it.
--   ADD COLUMN: metadata only (no default, or a constant one).
-- A server of the previous release started after this file keeps working: it inserts without the new columns, and the
-- default fills purpose.
SET LOCAL lock_timeout = '5s';

ALTER TABLE verification_codes
    ALTER COLUMN destination TYPE VARCHAR(254),
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'CONTACT',
    ADD COLUMN credential_version BIGINT;

ALTER TABLE password_reset_token
    ADD COLUMN credential_version BIGINT;
