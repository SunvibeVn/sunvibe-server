-- The purge of expired reset tokens (auth-email-access spec 5.7, D4-14).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on password_reset_token for its build (writes wait). One
-- row per reset requested, and the deploy runs with the old server stopped (spec O-3). Its duration is measured at O-1b
-- (SV-090).
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_password_reset_token_expires_at ON password_reset_token (expires_at);
