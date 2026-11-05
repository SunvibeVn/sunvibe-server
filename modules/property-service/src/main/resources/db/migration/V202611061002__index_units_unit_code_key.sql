-- Unit codes are unique in a property ignoring case and surrounding spaces (property-create intake spec 5.2 file 3, BR-8).
-- The existing uk_units_property_id_unit_code is case-sensitive and stays. COLLATE "C" keeps the expression immutable and
-- independent of the database locale, as chk_users_email_key_matches_email does.
-- Alone in its file: a plain CREATE UNIQUE INDEX holds a SHARE lock on units for its build, so writes wait. Size reason:
-- the owner confirmed on 2026-11-06 that no real database exists yet, so units is taken as 0 rows; spec 5.2 allows a plain
-- index under 100,000 rows with the server stopped, and section 9 runs again before the deploy. Above that count, replace
-- this file with CONCURRENTLY under the conditions of flyway.md before it is applied.
-- It fails on two codes of one property that differ only by case or spaces; the section 9 query proves there are none.
-- No IF NOT EXISTS: an index of this name with another definition must fail the start-up, not stand in for this rule.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_units_property_unit_code_key ON units (property_id, lower(btrim(unit_code) COLLATE "C"));
