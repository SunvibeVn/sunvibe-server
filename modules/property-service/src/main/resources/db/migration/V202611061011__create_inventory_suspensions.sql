-- A manager's processing restriction (property-create intake spec 4.11, 5.2 file 10, D5-16). It holds for the lead, so a new
-- intake cannot dodge it; intake_id is optional because a data-rights request can come before any intake. unit_id is a
-- plain id and NULL for the whole lead. reason and release_reason are kept for managers and never logged or sent to History.
SET LOCAL lock_timeout = '5s';

CREATE TABLE inventory_suspensions (
    id VARCHAR(26) NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    intake_id VARCHAR(26),
    unit_id VARCHAR(26),
    reason VARCHAR(2000) NOT NULL,
    created_by_user_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    release_reason VARCHAR(2000),
    released_by_user_id VARCHAR(26),
    released_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT inventory_suspensions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_inventory_suspensions_intake_id FOREIGN KEY (intake_id) REFERENCES inventory_intakes (id)
);

CREATE INDEX idx_inventory_suspensions_lead_id ON inventory_suspensions (lead_id);
CREATE INDEX idx_inventory_suspensions_intake_id ON inventory_suspensions (intake_id);
