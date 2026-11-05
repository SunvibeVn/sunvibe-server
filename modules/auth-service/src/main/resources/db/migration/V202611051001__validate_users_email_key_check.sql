-- Validates the check added by the previous file (auth-email-access spec 5.2 item 1). Every key is still NULL here, so
-- no row can fail; VALIDATE scans the table under SHARE UPDATE EXCLUSIVE, so reads and writes go on. ANALYZE takes the
-- same lock and restores the statistics of the email column, which the type change dropped.
SET LOCAL lock_timeout = '5s';

ALTER TABLE users VALIDATE CONSTRAINT chk_users_email_key_matches_email;
ANALYZE users (email);
