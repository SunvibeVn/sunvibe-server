-- The mandatory fees of an agreement version's confirmed terms (brokerage-agreement E05-S3 spec S3-D8, S3-D9; BR-8):
-- one row per fee, numbered 1 to 20 in the order of the list, written and read only through the agreement aggregate.
-- A fee is an amount in whole VND (ADR-006) with its charge unit, or included in the rent, or at the provider's rate.
-- A fee row is part of its version's content, so it goes with its version (ON DELETE CASCADE); no application path
-- deletes a version. The primary key also indexes the foreign key. A new table: no lock on populated data.
SET LOCAL lock_timeout = '5s';

CREATE TABLE brokerage_agreement_version_fees (
    version_id VARCHAR(26) NOT NULL,
    fee_no INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    basis VARCHAR(20) NOT NULL,
    amount NUMERIC(15, 0),
    charge_unit VARCHAR(30),
    CONSTRAINT pk_brokerage_agreement_version_fees PRIMARY KEY (version_id, fee_no),
    CONSTRAINT fk_brokerage_agreement_version_fees_version FOREIGN KEY (version_id)
        REFERENCES brokerage_agreement_versions (id) ON DELETE CASCADE,
    CONSTRAINT ck_brokerage_agreement_version_fees_number CHECK (fee_no BETWEEN 1 AND 20),
    CONSTRAINT ck_brokerage_agreement_version_fees_basis CHECK (basis IN ('AMOUNT', 'INCLUDED', 'PROVIDER_TARIFF')),
    CONSTRAINT ck_brokerage_agreement_version_fees_charge_unit CHECK (charge_unit IN ('PER_MONTH', 'PER_KWH', 'PER_M3',
        'PER_PERSON_PER_MONTH', 'PER_VEHICLE_PER_MONTH', 'ONE_OFF')),
    -- A CHECK passes when it yields NULL, so the amount is tested IS NOT NULL before its range.
    CONSTRAINT ck_brokerage_agreement_version_fees_amount CHECK (
        (basis = 'AMOUNT' AND amount IS NOT NULL AND amount BETWEEN 1 AND 999999999999999
            AND charge_unit IS NOT NULL)
        OR (basis <> 'AMOUNT' AND amount IS NULL AND charge_unit IS NULL))
);

COMMENT ON TABLE brokerage_agreement_version_fees IS
    'Mandatory fees of an agreement version (brokerage-agreement BR-8). Private values (BR-17): no free text other than '
    'the fee name. No retention policy yet (brief Q-2, SV-117).';
