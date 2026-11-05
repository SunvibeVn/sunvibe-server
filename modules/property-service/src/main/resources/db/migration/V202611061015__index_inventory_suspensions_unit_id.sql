-- A link looks for an unreleased suspension of its units on any lead (property-create intake spec 4.7), so a new intake
-- cannot dodge one. Partial: a whole-lead row has no unit, and a released row stops nothing. The table is new and empty.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_inventory_suspensions_active_unit_id ON inventory_suspensions (unit_id)
    WHERE unit_id IS NOT NULL AND released_at IS NULL;
