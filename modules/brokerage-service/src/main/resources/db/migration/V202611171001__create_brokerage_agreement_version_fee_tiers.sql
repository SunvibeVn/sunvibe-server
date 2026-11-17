-- The percentage tiers of an agreement version's brokerage fee and of its renewal fee (brokerage-agreement E05-S4 spec
-- S4-D3, S4-D4; BR-7): one row per tier, renewal FALSE for the fee's tiers and TRUE for the renewal's, numbered 1 to 5
-- in the order of the list, written and read only through the agreement aggregate. A tier covers the lease terms from
-- from_months up to the month before the next tier's start, at percent of one month's rent.
-- Only each row's bounds are held here. The first tier at 1 month and the strictly increasing starts (so no two equal
-- starts) are the aggregate's rules: a CHECK cannot see the other rows, and a unique index on the start would refuse
-- ordinary edits, which move a start from one row to another inside one flush when rows are merged by tier_no.
-- A tier row is part of its version's content, so it goes with its version (ON DELETE CASCADE); no application path
-- deletes a version. The primary key also indexes the foreign key. A new table: no lock on populated data.
SET LOCAL lock_timeout = '5s';

CREATE TABLE brokerage_agreement_version_fee_tiers (
    version_id VARCHAR(26) NOT NULL,
    renewal BOOLEAN NOT NULL,
    tier_no INTEGER NOT NULL,
    from_months INTEGER NOT NULL,
    percent INTEGER NOT NULL,
    CONSTRAINT pk_brokerage_agreement_version_fee_tiers PRIMARY KEY (version_id, renewal, tier_no),
    CONSTRAINT fk_brokerage_agreement_version_fee_tiers_version FOREIGN KEY (version_id)
        REFERENCES brokerage_agreement_versions (id) ON DELETE CASCADE,
    CONSTRAINT ck_brokerage_agreement_version_fee_tiers_number CHECK (tier_no BETWEEN 1 AND 5),
    CONSTRAINT ck_brokerage_agreement_version_fee_tiers_from_months CHECK (from_months BETWEEN 1 AND 120),
    CONSTRAINT ck_brokerage_agreement_version_fee_tiers_percent CHECK (percent BETWEEN 1 AND 300)
);

COMMENT ON TABLE brokerage_agreement_version_fee_tiers IS
    'Percentage tiers of an agreement version''s brokerage fee and renewal fee (brokerage-agreement BR-7). Private values '
    '(BR-17). No retention policy yet (brief Q-2, SV-117). The first start at 1 and the strictly increasing starts are '
    'the aggregate''s rules, not the table''s.';
