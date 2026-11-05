-- The inventory intake of a surveyed lead (property-create intake spec 4.1, 4.2, 5.2 file 5, I-1, I-2). A new table with
-- no foreign key: lead_id, source_checklist_id and the user ids name rows of other modules (flyway rules), and
-- target_property_id and result_property_id stay plain ids, because an FK to properties would block its writers until the
-- file ends (spec 5.2).
-- selection, target_mappings and availability are JSON arrays and used_versions a JSON object, stored as TEXT with an
-- IS JSON check, as survey_checklist_submissions.spaces is. They hold the selected spaces and the Sale's availability
-- notes: never logged.
-- intake_no is the highest of the lead plus one, taken under the lead lock.
CREATE TABLE inventory_intakes (
    id VARCHAR(26) NOT NULL,
    intake_no INTEGER NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    intent_cycle INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_checklist_id VARCHAR(26) NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    rental_scope VARCHAR(10) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    ward_code VARCHAR(10) NOT NULL,
    address_norm VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    selection TEXT NOT NULL,
    canonical_space_id VARCHAR(26),
    target_mode VARCHAR(10),
    target_property_id VARCHAR(36),
    target_mappings TEXT,
    availability TEXT,
    used_versions TEXT,
    result_property_id VARCHAR(36),
    preview_hash VARCHAR(64),
    completed_by_user_id VARCHAR(26),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT inventory_intakes_pkey PRIMARY KEY (id),
    CONSTRAINT uq_inventory_intakes_lead_intake_no UNIQUE (lead_id, intake_no),
    CONSTRAINT chk_inventory_intakes_intake_no CHECK (intake_no > 0),
    CONSTRAINT chk_inventory_intakes_status CHECK (status IN ('DRAFT', 'AWAITING_OWNER', 'OWNER_CONFIRMED', 'CASE_OPEN',
        'COMPLETED', 'ABANDONED')),
    CONSTRAINT chk_inventory_intakes_target_mode CHECK (target_mode IN ('CREATE', 'LINK')),
    CONSTRAINT chk_inventory_intakes_selection CHECK (selection IS JSON ARRAY),
    CONSTRAINT chk_inventory_intakes_target_mappings CHECK (target_mappings IS JSON ARRAY),
    CONSTRAINT chk_inventory_intakes_availability CHECK (availability IS JSON ARRAY),
    CONSTRAINT chk_inventory_intakes_used_versions CHECK (used_versions IS JSON OBJECT),
    CONSTRAINT chk_inventory_intakes_completed_result CHECK (status <> 'COMPLETED'
        OR (result_property_id IS NOT NULL AND completed_at IS NOT NULL)),
    CONSTRAINT chk_inventory_intakes_mappings_only_link CHECK (target_mappings IS NULL
        OR target_mode IS NOT DISTINCT FROM 'LINK')
);

-- I-1: one open intake per lead and cycle.
CREATE UNIQUE INDEX uq_inventory_intakes_open_per_cycle ON inventory_intakes (lead_id, intent_cycle)
    WHERE status NOT IN ('ABANDONED', 'COMPLETED');
-- I-2: one completed intake per source revision.
CREATE UNIQUE INDEX uq_inventory_intakes_completed_source ON inventory_intakes (source_checklist_id)
    WHERE status = 'COMPLETED';
CREATE INDEX idx_inventory_intakes_result_property_id ON inventory_intakes (result_property_id)
    WHERE result_property_id IS NOT NULL;
CREATE INDEX idx_inventory_intakes_lead_id ON inventory_intakes (lead_id);
CREATE INDEX idx_inventory_intakes_ward_code ON inventory_intakes (ward_code);
