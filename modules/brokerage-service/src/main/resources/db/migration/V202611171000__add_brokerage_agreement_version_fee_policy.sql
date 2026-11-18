-- The brokerage fee policy of an agreement version (brokerage-agreement E05-S4 spec S4-D3, S4-D4; BR-7): how the fee
-- is set (a percentage of one month's rent in tiers, a fixed amount in whole VND, or free of charge with a reason), the
-- days it is due after the lease is signed, what a renewal with the same tenant costs (not charged, a percentage or a
-- fixed amount), what is refunded when the lease ends early, and the protection days after the agreement ends. The
-- percentage tiers are rows of brokerage_agreement_version_fee_tiers. Who pays (the owner) and when the fee arises
-- (when a lease is signed) are fixed, so not stored. Every part may stay empty in a draft. Amounts are whole VND
-- (ADR-006); no currency column. The values are private (BR-17).
-- Cost on a populated table (flyway.md, spec 5.3): the table holds only the E05-S2 and E05-S3 drafts on staging and no
-- row in production. ADD COLUMN without a default changes metadata only; each CHECK is on columns added in this
-- statement, so its validation reads those few rows, all NULL, under the statement's ACCESS EXCLUSIVE lock, bounded by
-- lock_timeout. Existing rows read as no fee policy entered yet.
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreement_versions
    ADD COLUMN fee_formula_type VARCHAR(20),
    ADD COLUMN fee_fixed_amount NUMERIC(15, 0),
    ADD COLUMN fee_free_reason VARCHAR(500),
    ADD COLUMN fee_due_days INTEGER,
    ADD COLUMN renewal_fee_type VARCHAR(20),
    ADD COLUMN renewal_fee_fixed_amount NUMERIC(15, 0),
    ADD COLUMN early_lease_end_type VARCHAR(20),
    ADD COLUMN early_lease_end_refund_percent INTEGER,
    ADD COLUMN early_lease_end_within_months INTEGER,
    ADD COLUMN protection_days INTEGER,
    -- A CHECK passes when it yields NULL, so each branch tests its columns IS NOT NULL before comparing them.
    ADD CONSTRAINT ck_brokerage_agreement_versions_fee_formula
        CHECK ((fee_formula_type IS NULL AND fee_fixed_amount IS NULL AND fee_free_reason IS NULL)
            OR (fee_formula_type IS NOT NULL AND fee_formula_type = 'PERCENT_OF_RENT' AND fee_fixed_amount IS NULL
                AND fee_free_reason IS NULL)
            OR (fee_formula_type IS NOT NULL AND fee_formula_type = 'FIXED_AMOUNT' AND fee_fixed_amount IS NOT NULL
                AND fee_fixed_amount BETWEEN 1 AND 999999999999999 AND fee_free_reason IS NULL)
            OR (fee_formula_type IS NOT NULL AND fee_formula_type = 'FREE' AND fee_free_reason IS NOT NULL
                AND fee_fixed_amount IS NULL)),
    ADD CONSTRAINT ck_brokerage_agreement_versions_fee_due_days
        CHECK (fee_due_days BETWEEN 0 AND 30),
    ADD CONSTRAINT ck_brokerage_agreement_versions_renewal_fee
        CHECK ((renewal_fee_type IS NULL AND renewal_fee_fixed_amount IS NULL)
            OR (renewal_fee_type IS NOT NULL AND renewal_fee_type IN ('NOT_CHARGED', 'PERCENT_OF_RENT')
                AND renewal_fee_fixed_amount IS NULL)
            OR (renewal_fee_type IS NOT NULL AND renewal_fee_type = 'FIXED_AMOUNT'
                AND renewal_fee_fixed_amount IS NOT NULL
                AND renewal_fee_fixed_amount BETWEEN 1 AND 999999999999999)),
    ADD CONSTRAINT ck_brokerage_agreement_versions_early_lease_end
        CHECK ((early_lease_end_type IS NULL AND early_lease_end_refund_percent IS NULL
                AND early_lease_end_within_months IS NULL)
            OR (early_lease_end_type IS NOT NULL AND early_lease_end_type = 'NO_REFUND'
                AND early_lease_end_refund_percent IS NULL AND early_lease_end_within_months IS NULL)
            OR (early_lease_end_type IS NOT NULL AND early_lease_end_type = 'PARTIAL_REFUND'
                AND early_lease_end_refund_percent IS NOT NULL AND early_lease_end_refund_percent BETWEEN 1 AND 100
                AND early_lease_end_within_months IS NOT NULL AND early_lease_end_within_months BETWEEN 1 AND 12)),
    ADD CONSTRAINT ck_brokerage_agreement_versions_protection_days
        CHECK (protection_days BETWEEN 0 AND 180);
