-- The duplicate signal reads the properties of a ward by code and normalized address (property-create intake spec 4.10,
-- 5.2 file 3, D5-14). Legacy rows have no ward_code and are matched by normalized ward name in Java (spec 4.10).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on properties for its build, so writes wait. Size reason: the
-- owner confirmed on 2026-11-06 that no real database exists yet, so properties is taken as 0 rows; spec 5.2 allows a plain
-- index under 100,000 rows with the server stopped, and section 9 runs again before the deploy. Above that count, replace
-- this file with CONCURRENTLY under the conditions of flyway.md before it is applied.
-- No IF NOT EXISTS: an index of this name with another definition must fail the start-up.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_properties_ward_code_address_norm ON properties (ward_code, address_norm);
