-- The purge of expired codes (auth-email-access spec 5.7, D4-14).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on verification_codes for its build (writes wait). One row
-- per code sent, and the deploy runs with the old server stopped (spec O-3). Its duration is measured at O-1b (SV-090).
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_verification_codes_expires_at ON verification_codes (expires_at);
