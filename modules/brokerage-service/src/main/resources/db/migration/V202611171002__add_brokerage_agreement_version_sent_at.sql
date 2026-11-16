-- When an agreement version was sent to the owner (brokerage-agreement E05-S4 spec S4-D4, S4-D6; US-1 AC-5, BR-5,
-- BR-10: the owner's 7-day answer window starts here). A draft is a version not sent yet, so a DRAFT row has none. No
-- CHECK requires the time on the later states: fixture rows of earlier tests insert such versions directly, and the
-- aggregate is the only write path.
-- Cost on a populated table (flyway.md, spec 5.3): the table holds only the E05-S2 and E05-S3 drafts on staging and no
-- row in production. ADD COLUMN without a default changes metadata only; the CHECK reads those few rows, all NULL in
-- the new column, under the statement's ACCESS EXCLUSIVE lock, bounded by lock_timeout. Existing rows read as not sent.
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreement_versions
    ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_brokerage_agreement_versions_sent
        CHECK (status <> 'DRAFT' OR sent_at IS NULL);
