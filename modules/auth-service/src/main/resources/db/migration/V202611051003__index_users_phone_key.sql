-- At most one live row holds a canonical phone (auth-email-access spec 5.2 item 2, I-2, BR-22). Raw idx_users_username
-- stays: it blocks the national form of a number whose row has no key (I-3).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on users for its build (writes wait). The build scans users
-- once, and the deploy runs with the old server stopped (spec O-3). Its duration is measured at O-1b (SV-090).
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_users_phone_key ON users (phone_key) WHERE deleted_at IS NULL;
