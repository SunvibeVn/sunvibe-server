-- One property per intake (property-create intake spec 5.2 file 3, US-6): a retry or a race cannot create a second one.
-- Partial: legacy rows have no intake id, so the build covers no row of today's data, though it still scans the table.
-- Alone in its file: a plain CREATE UNIQUE INDEX holds a SHARE lock on properties for its build, so writes wait. Size
-- reason: the owner confirmed on 2026-11-06 that no real database exists yet, so properties is taken as 0 rows; spec 5.2
-- allows a plain index under 100,000 rows with the server stopped, and section 9 runs again before the deploy. Above that
-- count, replace this file with CONCURRENTLY under the conditions of flyway.md before it is applied.
-- No IF NOT EXISTS: an index of this name with another definition must fail the start-up.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_properties_intake_id ON properties (intake_id) WHERE intake_id IS NOT NULL;
