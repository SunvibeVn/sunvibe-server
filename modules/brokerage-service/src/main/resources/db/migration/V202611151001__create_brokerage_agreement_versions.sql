-- The versions of a brokerage agreement (brokerage-agreement E05-S2 spec 5.3, S2-D3; BR-5). Each version rests on its
-- own intake basis (BR-2): basis_lead_id and basis_intake_id are plain ids of other modules, as created_by_user_id
-- (the drafter). start_date and end_date are calendar dates in Vietnam time; the period CHECK repeats BR-6 (the end
-- not before the start, at most 12 months after it; + INTERVAL '12 months' clamps to the month's last day as Java's
-- plusMonths does). No free text (BR-17). The status CHECK holds the whole BR-5 vocabulary (S2-D4).
-- uq_brokerage_agreement_versions_pending keeps one pending version (DRAFT to ACTIVATED) per agreement (BR-5, S2-D7).
-- It counts the stored status, so every time-driven exit from the pending set (a lapse, the start of an activated
-- version, the cancellation at the agreement's end) is a stored transition written when due (S2-D4).
-- uq_brokerage_agreement_versions_number also indexes the foreign key.
SET LOCAL lock_timeout = '5s';

CREATE TABLE brokerage_agreement_versions (
    id VARCHAR(26) NOT NULL,
    agreement_id VARCHAR(26) NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    basis_lead_id VARCHAR(26) NOT NULL,
    basis_intake_id VARCHAR(26) NOT NULL,
    created_by_user_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT brokerage_agreement_versions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_brokerage_agreement_versions_agreement FOREIGN KEY (agreement_id)
        REFERENCES brokerage_agreements (id),
    CONSTRAINT ck_brokerage_agreement_versions_number CHECK (version_no > 0),
    CONSTRAINT ck_brokerage_agreement_versions_status CHECK (status IN ('DRAFT', 'AWAITING_OWNER', 'OWNER_CONFIRMED',
        'ACTIVATED', 'EFFECTIVE', 'SUPERSEDED', 'OWNER_REJECTED', 'LAPSED', 'ACTIVATION_REFUSED', 'CANCELLED')),
    CONSTRAINT ck_brokerage_agreement_versions_period CHECK (end_date >= start_date
        AND end_date <= start_date + INTERVAL '12 months')
);

CREATE UNIQUE INDEX uq_brokerage_agreement_versions_number ON brokerage_agreement_versions (agreement_id, version_no);
CREATE UNIQUE INDEX uq_brokerage_agreement_versions_pending ON brokerage_agreement_versions (agreement_id)
    WHERE status IN ('DRAFT', 'AWAITING_OWNER', 'OWNER_CONFIRMED', 'ACTIVATED');
