-- The per-destination daily cap of bind codes (auth-email-access spec 4.7, 5.2 item 3).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on verification_codes for its build (writes wait). Until
-- the purge of this release runs, the table holds every code ever sent, one row per code, and the deploy runs with the
-- old server stopped (spec O-3). Its duration is measured at O-1b (SV-090). ANALYZE restores the statistics of the
-- column, which the type change dropped.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_verification_codes_destination ON verification_codes (destination, created_at);
ANALYZE verification_codes (destination);
