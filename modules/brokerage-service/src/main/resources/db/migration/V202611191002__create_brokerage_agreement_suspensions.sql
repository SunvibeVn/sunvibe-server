-- The suspensions of an agreement (brokerage-agreement E05-S8 spec S8-D6, S8-D9; BR-15, BR-16, BR-17): one row per
-- suspension, who suspended it, when, why and an optional note; once lifted, who lifted it, when and why. The agreement
-- row holds the state SUSPENDED; these rows hold why and by whom, written and read only through
-- AgreementSuspensionDomainRepository in the agreement's transaction. suspended_by and released_by name users of another
-- module and are plain ids. The note and the release reason are private (BR-17): never in a history row, a log line,
-- an error or an outbox payload; no retention rule exists yet (brief Q-2, register SV-117).
-- A record goes with its agreement (ON DELETE CASCADE), as the fee and tier rows go with their version; no application
-- path deletes an agreement. uq_brokerage_agreement_suspensions_open keeps at most one open record per agreement (S8-D6);
-- idx_brokerage_agreement_suspensions_agreement indexes the foreign key with the detail's newest-first order.
-- Cost on a populated table (flyway.md, spec S8-D9): the new table is empty, so its indexes build at once; its foreign
-- key takes a SHARE ROW EXCLUSIVE lock on brokerage_agreements, which blocks the inserts, updates and deletes of
-- agreements (not their reads) until this file commits. lock_timeout bounds the wait for it, and the file holds only the
-- table and its two indexes, so the lock is held for milliseconds once granted.
SET LOCAL lock_timeout = '5s';

CREATE TABLE brokerage_agreement_suspensions (
    id VARCHAR(26) NOT NULL,
    agreement_id VARCHAR(26) NOT NULL,
    reason_type VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    suspended_by VARCHAR(26) NOT NULL,
    suspended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_by VARCHAR(26),
    released_at TIMESTAMP WITH TIME ZONE,
    release_reason VARCHAR(500),
    CONSTRAINT pk_brokerage_agreement_suspensions PRIMARY KEY (id),
    CONSTRAINT fk_brokerage_agreement_suspensions_agreement FOREIGN KEY (agreement_id)
        REFERENCES brokerage_agreements (id) ON DELETE CASCADE,
    CONSTRAINT ck_brokerage_agreement_suspensions_reason_type
        CHECK (reason_type IN ('COMPLAINT', 'SUSPECTED_FRAUD', 'DISPUTE', 'NEEDS_CHECK')),
    -- Open (no release column), or lifted with who, when and why, not before it began. A CHECK passes when it yields
    -- NULL, so each branch tests its columns IS NULL / IS NOT NULL first.
    CONSTRAINT ck_brokerage_agreement_suspensions_release
        CHECK ((released_by IS NULL AND released_at IS NULL AND release_reason IS NULL)
            OR (released_by IS NOT NULL AND released_at IS NOT NULL AND release_reason IS NOT NULL
                AND released_at >= suspended_at))
);

CREATE INDEX idx_brokerage_agreement_suspensions_agreement
    ON brokerage_agreement_suspensions (agreement_id, suspended_at DESC);
CREATE UNIQUE INDEX uq_brokerage_agreement_suspensions_open
    ON brokerage_agreement_suspensions (agreement_id) WHERE released_at IS NULL;

COMMENT ON TABLE brokerage_agreement_suspensions IS
    'Suspensions of a brokerage agreement (brokerage-agreement BR-15). Private values (BR-17): the note and the release '
    'reason. No retention policy yet (brief Q-2, SV-117).';
