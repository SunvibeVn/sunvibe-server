-- Wave W8 (property-units brief BR-14, rebuild BR-010; spec section 4): version number for optimistic locking, used
-- once properties can be edited. Existing rows start at 0. Expand-only (BR-13): no other column changes. The default
-- keeps the app that is already running compatible: it does not know the column and its inserts get 0.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
ALTER TABLE properties ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
