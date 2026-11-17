-- The owner's answer to a sent agreement version (brokerage-agreement E05-S5 spec S5-D11; US-2 AC-2, AC-3; BR-5, BR-10,
-- BR-17): CONFIRMED or REJECTED, by which account and when, the confirmation statement version read, and the optional
-- reason of a rejection. An answer is given once and never changes (BR-5). The reason and the statement version are
-- private (BR-17): never in a history row or a log line; no retention rule exists yet, so nothing is deleted or
-- exported (brief Q-2, register SV-117). No column holds the lapse: its deadline is sent_at + 7 days and a stored lapse
-- is the version's status (spec S5-D8).
-- No CHECK requires an answer on OWNER_CONFIRMED or OWNER_REJECTED: fixture rows of earlier tests insert such versions
-- directly, and the aggregate is the only write path.
-- Cost on a populated table (flyway.md, spec 5.3): the table holds only the E05 drafts and sent versions on staging and no
-- row in production. ADD COLUMN without a default changes metadata only; the CHECK reads those few rows, all NULL in the
-- new columns, under the statement's ACCESS EXCLUSIVE lock, bounded by lock_timeout. Existing rows read as not answered.
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreement_versions
    ADD COLUMN owner_answer VARCHAR(10),
    ADD COLUMN owner_answered_by VARCHAR(26),
    ADD COLUMN owner_answered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN owner_statement_version VARCHAR(40),
    ADD COLUMN owner_rejection_reason VARCHAR(500),
    -- Either no answer at all, or an answer with its account and time on a version that was sent and no longer waits:
    -- a confirmation holds its statement version and no reason, a rejection no statement version and an optional
    -- reason. A CHECK passes when it yields NULL, so each branch tests its columns IS NULL / IS NOT NULL first.
    ADD CONSTRAINT ck_brokerage_agreement_versions_owner_answer
        CHECK ((owner_answer IS NULL AND owner_answered_by IS NULL AND owner_answered_at IS NULL
                AND owner_statement_version IS NULL AND owner_rejection_reason IS NULL)
            OR (owner_answer IS NOT NULL AND owner_answered_by IS NOT NULL AND owner_answered_at IS NOT NULL
                AND sent_at IS NOT NULL AND status NOT IN ('DRAFT', 'AWAITING_OWNER')
                AND ((owner_answer = 'CONFIRMED' AND owner_statement_version IS NOT NULL
                        AND owner_rejection_reason IS NULL)
                    OR (owner_answer = 'REJECTED' AND owner_statement_version IS NULL))));
