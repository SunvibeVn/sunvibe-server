-- The owner's own confirmation and the manager's check of it (property-create intake spec 4.6, 5.2 file 7, I-3, D5-7, D5-10,
-- A-14). sign_in_losses is the count the proof read from the account: the proof is live only while the account's count is
-- equal, so losing sign-in kills it without two clocks to compare. phrase_hash is the check phrase salted by this row's id;
-- it is cleared on DONE or the proof's death, and phrase_expires_at stays as the record of a start. owner_check_basis is
-- kept for managers and never logged. user_id and owner_check_by_user_id name users of auth-service; lead_id and
-- checklist_id name rows of other modules (no FK across modules).
-- The second half: inventory_intakes gains proof_id, the proof in force, set at completion and restoration. The table is
-- new and empty, so its CHECK needs no NOT VALID step.
SET LOCAL lock_timeout = '5s';

CREATE TABLE inventory_owner_proofs (
    id VARCHAR(26) NOT NULL,
    intake_id VARCHAR(26) NOT NULL,
    request_id VARCHAR(26) NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    intent_cycle INTEGER NOT NULL,
    checklist_id VARCHAR(26) NOT NULL,
    user_id VARCHAR(26) NOT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    statement_version VARCHAR(40) NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    voided_at TIMESTAMP WITH TIME ZONE,
    owner_check VARCHAR(10) NOT NULL,
    sign_in_losses INTEGER NOT NULL,
    phrase_hash VARCHAR(64),
    phrase_expires_at TIMESTAMP WITH TIME ZONE,
    phrase_failed_attempts INTEGER NOT NULL DEFAULT 0,
    owner_check_by_user_id VARCHAR(26),
    owner_check_basis VARCHAR(2000),
    owner_check_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT inventory_owner_proofs_pkey PRIMARY KEY (id),
    CONSTRAINT fk_inventory_owner_proofs_intake_id FOREIGN KEY (intake_id) REFERENCES inventory_intakes (id),
    CONSTRAINT fk_inventory_owner_proofs_request_id FOREIGN KEY (request_id) REFERENCES inventory_owner_requests (id),
    CONSTRAINT uq_inventory_owner_proofs_request_id UNIQUE (request_id),
    CONSTRAINT chk_inventory_owner_proofs_owner_check CHECK (owner_check IN ('REQUIRED', 'DONE', 'REFUSED'))
);

-- One proof neither withdrawn nor voided per intake (I-3): a confirm voids any such earlier row, live or not.
CREATE UNIQUE INDEX uq_inventory_owner_proofs_in_force ON inventory_owner_proofs (intake_id)
    WHERE withdrawn_at IS NULL AND voided_at IS NULL;
CREATE INDEX idx_inventory_owner_proofs_intake_id ON inventory_owner_proofs (intake_id);
CREATE INDEX idx_inventory_owner_proofs_user_id ON inventory_owner_proofs (user_id);
CREATE INDEX idx_inventory_owner_proofs_lead_id ON inventory_owner_proofs (lead_id);

ALTER TABLE inventory_intakes
    ADD COLUMN proof_id VARCHAR(26),
    ADD CONSTRAINT fk_inventory_intakes_proof_id FOREIGN KEY (proof_id) REFERENCES inventory_owner_proofs (id),
    ADD CONSTRAINT chk_inventory_intakes_completed_proof CHECK (status <> 'COMPLETED' OR proof_id IS NOT NULL);

CREATE INDEX idx_inventory_intakes_proof_id ON inventory_intakes (proof_id);
