-- A manager's check of a possible duplicate (property-create intake spec 4.10, 5.2 file 9, BR-21, D5-14, D5-15). pair_key
-- names the pair and the scopes; a new selection is a new pair. Its index is partial, so several CLOSED rows can share a
-- key and a case ended by an exit can open again. The insert uses ON CONFLICT with this predicate spelled the same way.
-- scope_hash belongs to intake_id and candidate_scope_hash to candidate_intake_id, as full hashes; pair_key holds their
-- first 16 hex characters. basis is kept for managers and never logged. candidate_property_id is a plain id: an FK to
-- properties would block its writers until the file ends (spec 5.2). decided_by_user_id names a user of auth-service.
SET LOCAL lock_timeout = '5s';

CREATE TABLE inventory_duplicate_cases (
    id VARCHAR(26) NOT NULL,
    intake_id VARCHAR(26) NOT NULL,
    candidate_property_id VARCHAR(36),
    candidate_intake_id VARCHAR(26),
    pair_key VARCHAR(120) NOT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    candidate_scope_hash VARCHAR(64),
    signal VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    outcome VARCHAR(20),
    proceeding_intake_id VARCHAR(26),
    basis VARCHAR(2000),
    decided_by_user_id VARCHAR(26),
    decided_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT inventory_duplicate_cases_pkey PRIMARY KEY (id),
    CONSTRAINT fk_inventory_duplicate_cases_intake_id FOREIGN KEY (intake_id) REFERENCES inventory_intakes (id),
    CONSTRAINT fk_inventory_duplicate_cases_candidate_intake_id FOREIGN KEY (candidate_intake_id)
        REFERENCES inventory_intakes (id),
    CONSTRAINT fk_inventory_duplicate_cases_proceeding_intake_id FOREIGN KEY (proceeding_intake_id)
        REFERENCES inventory_intakes (id),
    CONSTRAINT chk_inventory_duplicate_cases_signal CHECK (signal IN ('ADDRESS', 'PIN', 'INTAKE', 'LINK_PLACE')),
    CONSTRAINT chk_inventory_duplicate_cases_status CHECK (status IN ('OPEN', 'DECIDED', 'CLOSED')),
    CONSTRAINT chk_inventory_duplicate_cases_outcome CHECK (outcome IN ('DIFFERENT_ASSET', 'SAME_ASSET')),
    CONSTRAINT chk_inventory_duplicate_cases_one_candidate CHECK (
        (candidate_property_id IS NOT NULL) <> (candidate_intake_id IS NOT NULL)),
    CONSTRAINT chk_inventory_duplicate_cases_decided_outcome CHECK ((status = 'DECIDED') = (outcome IS NOT NULL)),
    CONSTRAINT chk_inventory_duplicate_cases_decided_at CHECK (status <> 'DECIDED' OR decided_at IS NOT NULL),
    CONSTRAINT chk_inventory_duplicate_cases_intake_signal CHECK (
        (signal = 'INTAKE') = (candidate_intake_id IS NOT NULL)),
    CONSTRAINT chk_inventory_duplicate_cases_candidate_scope CHECK (
        (candidate_scope_hash IS NOT NULL) = (candidate_intake_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_inventory_duplicate_cases_pair_key ON inventory_duplicate_cases (pair_key)
    WHERE status <> 'CLOSED';
CREATE INDEX idx_inventory_duplicate_cases_intake_id ON inventory_duplicate_cases (intake_id);
CREATE INDEX idx_inventory_duplicate_cases_proceeding_intake_id ON inventory_duplicate_cases (proceeding_intake_id);
CREATE INDEX idx_inventory_duplicate_cases_status_created ON inventory_duplicate_cases (status, created_at, id);
CREATE INDEX idx_inventory_duplicate_cases_candidate_intake_id ON inventory_duplicate_cases (candidate_intake_id);
CREATE INDEX idx_inventory_duplicate_cases_candidate_property_id ON inventory_duplicate_cases (candidate_property_id);
