-- At most one live row holds a canonical email (auth-email-access spec 5.2 item 2, I-2, BR-22): unverified, PENDING and
-- LOCK rows included; a soft-deleted row frees its key. Raw idx_users_email stays.
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on users for its build (writes wait). The build scans users
-- once, and the deploy runs with the old server stopped (spec O-3), so nothing waits behind it. Its duration on
-- populated data is measured at O-1b (SV-090).
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_users_email_key ON users (email_key) WHERE deleted_at IS NULL;
