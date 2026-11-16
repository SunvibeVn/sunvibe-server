-- The content of an agreement version (brokerage-agreement E05-S3 spec S3-D7 to S3-D9; BR-8, BR-9): the public photo
-- consent (photo_consent: interior and common areas, NULL = not answered yet in a draft; exterior_photo_consent: a
-- separate choice, FALSE by default, TRUE only with photo_consent TRUE) and the confirmed terms (rent, deposit,
-- minimum term, payment period, and whether the mandatory fee list is stated; the fees are rows of
-- brokerage_agreement_version_fees). Every part may stay empty in a draft. Amounts are whole VND (ADR-006); no
-- currency column. The values are private (BR-17): no free text here.
-- Cost on a populated table (flyway.md, spec 5.3): the table holds only E05-S2's drafts on staging and no row in
-- production. ADD COLUMN with no default or a constant default changes metadata only; each CHECK is on columns added
-- in this statement, so its validation reads those few rows, all NULL or default, under the statement's ACCESS
-- EXCLUSIVE lock, bounded by lock_timeout. Existing rows read as nothing entered yet (S3-D9).
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreement_versions
    ADD COLUMN photo_consent BOOLEAN,
    ADD COLUMN exterior_photo_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN monthly_rent_amount NUMERIC(15, 0),
    ADD COLUMN deposit_basis VARCHAR(10),
    ADD COLUMN deposit_months INTEGER,
    ADD COLUMN deposit_amount NUMERIC(15, 0),
    ADD COLUMN minimum_term_months INTEGER,
    ADD COLUMN payment_period_months INTEGER,
    ADD COLUMN mandatory_fees_stated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_brokerage_agreement_versions_exterior_photo
        CHECK (NOT exterior_photo_consent OR photo_consent IS TRUE),
    ADD CONSTRAINT ck_brokerage_agreement_versions_monthly_rent
        CHECK (monthly_rent_amount BETWEEN 1 AND 999999999999999),
    -- A CHECK passes when it yields NULL, so each branch tests its columns IS NOT NULL before comparing them.
    ADD CONSTRAINT ck_brokerage_agreement_versions_deposit
        CHECK ((deposit_basis IS NULL AND deposit_months IS NULL AND deposit_amount IS NULL)
            OR (deposit_basis IS NOT NULL AND deposit_basis = 'MONTHS' AND deposit_months IS NOT NULL
                AND deposit_months BETWEEN 0 AND 12 AND deposit_amount IS NULL)
            OR (deposit_basis IS NOT NULL AND deposit_basis = 'AMOUNT' AND deposit_amount IS NOT NULL
                AND deposit_amount BETWEEN 0 AND 999999999999999 AND deposit_months IS NULL)),
    ADD CONSTRAINT ck_brokerage_agreement_versions_minimum_term
        CHECK (minimum_term_months BETWEEN 1 AND 120),
    ADD CONSTRAINT ck_brokerage_agreement_versions_payment_period
        CHECK (payment_period_months BETWEEN 1 AND 12);
