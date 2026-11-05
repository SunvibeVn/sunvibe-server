-- A request that the owner confirm an account link (property-create intake spec 4.6, 5.2 file 6, D5-5, D5-6, A-5).
-- target_user_id is NULL when the email matched no confirmable account: the row exists all the same, on the same code
-- path, so the answer tells a Sale nothing. code_hash is the SHA-256 of the code salted by this row's id; the code alone
-- gives nothing, and it is never stored or logged. display_snapshot is what the owner reads after entering the code, as
-- the owner side has no lead viewer; it holds an address, so it is cleared when the request dies (spec 4.6 step 1) and
-- never logged. target_user_id and created_by_user_id name users of auth-service (no FK across modules).
SET LOCAL lock_timeout = '5s';

CREATE TABLE inventory_owner_requests (
    id VARCHAR(26) NOT NULL,
    intake_id VARCHAR(26) NOT NULL,
    target_user_id VARCHAR(26),
    code_hash VARCHAR(64) NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    display_snapshot TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    voided_at TIMESTAMP WITH TIME ZONE,
    declined_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT inventory_owner_requests_pkey PRIMARY KEY (id),
    CONSTRAINT fk_inventory_owner_requests_intake_id FOREIGN KEY (intake_id) REFERENCES inventory_intakes (id),
    CONSTRAINT chk_inventory_owner_requests_display_snapshot CHECK (display_snapshot IS JSON OBJECT)
);

-- One open request per intake: a new one voids the old in the same command.
CREATE UNIQUE INDEX uq_inventory_owner_requests_open ON inventory_owner_requests (intake_id)
    WHERE consumed_at IS NULL AND voided_at IS NULL AND declined_at IS NULL;
CREATE INDEX idx_inventory_owner_requests_intake_id ON inventory_owner_requests (intake_id);
CREATE INDEX idx_inventory_owner_requests_target_user_id ON inventory_owner_requests (target_user_id);
CREATE INDEX idx_inventory_owner_requests_created_by ON inventory_owner_requests (created_by_user_id);
