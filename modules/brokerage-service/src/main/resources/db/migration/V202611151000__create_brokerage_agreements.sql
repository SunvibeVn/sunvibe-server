-- Brokerage agreements (brokerage-agreement E05-S2 spec 5.3, S2-D3): one row per agreement between Sunvibe and the
-- declared owner of one unit for one transaction type (BR-1). unit_code is the code read from the intake basis at
-- creation; basis_lead_id is the basis lead of the latest version, kept equal by the aggregate. unit_id, property_id,
-- owner_user_id, basis_lead_id and created_by_user_id name rows of other modules and are plain ids. No free text
-- (BR-17). The status CHECK holds the whole BR-5 vocabulary (S2-D4).
-- uq_brokerage_agreements_unfinished_unit keeps one unfinished agreement (DRAFTING, AWAITING_CONFIRMATION, ACTIVE,
-- SUSPENDED) per unit and type (BR-1, S2-D7). It counts the stored status, so every time-driven exit from the
-- unfinished set (the expiry at the end of the period) is a stored transition written when due, and a command that
-- needs the unit's slot applies a due one first (S2-D4).
SET LOCAL lock_timeout = '5s';

CREATE TABLE brokerage_agreements (
    id VARCHAR(26) NOT NULL,
    unit_id VARCHAR(26) NOT NULL,
    unit_code VARCHAR(50),
    property_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    owner_user_id VARCHAR(26) NOT NULL,
    basis_lead_id VARCHAR(26) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by_user_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT brokerage_agreements_pkey PRIMARY KEY (id),
    CONSTRAINT ck_brokerage_agreements_transaction_type CHECK (transaction_type IN ('LONG_TERM_RENT')),
    CONSTRAINT ck_brokerage_agreements_status CHECK (status IN ('DRAFTING', 'AWAITING_CONFIRMATION', 'ACTIVE',
        'SUSPENDED', 'EXPIRED', 'TERMINATED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_brokerage_agreements_unfinished_unit ON brokerage_agreements (unit_id, transaction_type)
    WHERE status IN ('DRAFTING', 'AWAITING_CONFIRMATION', 'ACTIVE', 'SUSPENDED');
CREATE INDEX idx_brokerage_agreements_basis_lead_id ON brokerage_agreements (basis_lead_id);
