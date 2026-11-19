-- The end of an agreement (brokerage-agreement E05-S8 spec S8-D9, 5.1; BR-5, BR-6, BR-14, BR-17): ended_at is when it
-- ended (the end of its version for an expiry, the command's moment for a termination or a cancellation); the
-- termination columns are what staff recorded (S8-D5): the type, the reported date, the channel of an owner's
-- withdrawal, whether Sunvibe introduced the tenant of a unit let, and an optional note (required for Sunvibe's own
-- decision). The date, the channel, the answer on the tenant and the note are private (BR-17): never in a history row, a
-- log line, an error or an outbox payload; no retention rule exists yet (brief Q-2, register SV-117).
-- Both CHECKs bind one way only: an agreement that has not ended holds no end, and only a TERMINATED one holds the
-- termination columns, in the combination its type takes. A final state is not required to hold them: test fixtures
-- insert final states without them, and the aggregate is the only write path (the precedent of V202611171501 and
-- V202611181000).
-- Cost on a populated table (flyway.md, spec S8-D9): ADD COLUMN without a default changes metadata only, and the CHECKs
-- are added NOT VALID, so no existing row is read here (the next file validates them). Both take an ACCESS EXCLUSIVE lock
-- on brokerage_agreements, which blocks its reads and writes until this file commits; lock_timeout bounds the wait for
-- it, and the file holds only these metadata changes, so the lock is held for milliseconds once granted. Existing rows
-- read as "not ended".
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreements
    ADD COLUMN ended_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN termination_type VARCHAR(30),
    ADD COLUMN termination_reported_on DATE,
    ADD COLUMN termination_channel VARCHAR(20),
    ADD COLUMN termination_introduced VARCHAR(10),
    ADD COLUMN termination_note VARCHAR(500),
    ADD CONSTRAINT ck_brokerage_agreements_end
        CHECK (ended_at IS NULL OR status IN ('EXPIRED', 'TERMINATED', 'CANCELLED'))
        NOT VALID,
    -- Either no termination column at all, or a TERMINATED agreement with its end and the columns of its type: an
    -- owner's withdrawal holds its date and channel, a unit let its date and the answer on the tenant, Sunvibe's own
    -- decision its note and no date; no type holds a column of another. A CHECK passes when it yields NULL, so each
    -- branch tests its columns IS NULL / IS NOT NULL first.
    ADD CONSTRAINT ck_brokerage_agreements_termination
        CHECK ((termination_type IS NULL AND termination_reported_on IS NULL AND termination_channel IS NULL
                AND termination_introduced IS NULL AND termination_note IS NULL)
            OR (termination_type IS NOT NULL AND status = 'TERMINATED' AND ended_at IS NOT NULL
                AND ((termination_type = 'OWNER_WITHDREW' AND termination_reported_on IS NOT NULL
                        AND termination_channel IS NOT NULL
                        AND termination_channel IN ('PHONE_CALL', 'IN_PERSON', 'MESSAGE', 'EMAIL', 'OTHER')
                        AND termination_introduced IS NULL)
                    OR (termination_type = 'UNIT_LET' AND termination_reported_on IS NOT NULL
                        AND termination_introduced IS NOT NULL
                        AND termination_introduced IN ('YES', 'NO', 'UNKNOWN')
                        AND termination_channel IS NULL)
                    OR (termination_type = 'SUNVIBE_TERMINATED' AND termination_note IS NOT NULL
                        AND termination_reported_on IS NULL AND termination_channel IS NULL
                        AND termination_introduced IS NULL))))
        NOT VALID;
