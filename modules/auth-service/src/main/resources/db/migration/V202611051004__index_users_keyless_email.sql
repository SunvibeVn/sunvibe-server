-- Finds a live row whose stored email has no key (auth-email-access spec 5.2 item 2, I-3): such a row still blocks
-- registering or binding an equivalent address. The expression is the one of chk_users_email_key_matches_email. Not
-- unique: a collision of legacy rows stays without a key on both sides and is never resolved here (D4-7).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on users for its build (writes wait). The build scans users
-- once, and the deploy runs with the old server stopped (spec O-3). Its duration is measured at O-1b (SV-090).
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_users_keyless_email ON users ((lower(btrim(email) COLLATE "C")))
    WHERE deleted_at IS NULL AND email IS NOT NULL AND email_key IS NULL;
