-- The activation decision on a confirmed agreement version (brokerage-agreement E05-S6 spec S6-D3, S6-D4, S6-D14; US-3
-- AC-1 to AC-3; BR-5, BR-11, BR-17): ACTIVATED or REFUSED, by which manager and when; for an activation the statement
-- version made and the signed document as written on it (number, signing date, Sunvibe's signatory's name and title,
-- never a copy of the document, BR-11); for a refusal its type and an optional note. A decision is made once and never
-- changes (BR-5). The document's values and the note are private (BR-17): never in a history row or a log line; no
-- retention rule exists yet, so nothing is deleted or exported (brief Q-2, register SV-117). No column holds the
-- 14-day deadline, the start or the end: each is derived (owner_answered_at + 14 days; the start and end dates in
-- Vietnam).
-- No CHECK requires a decision on ACTIVATED, EFFECTIVE or ACTIVATION_REFUSED: fixture rows of earlier tests insert such
-- versions directly, and the aggregate is the only write path.
-- Cost on a populated table (flyway.md, spec 5.3): the table holds only the E05 drafts and sent versions on staging and no
-- row in production. ADD COLUMN without a default changes metadata only; the CHECK is added NOT VALID, so it reads no
-- existing row here (the next file validates it). The ACCESS EXCLUSIVE lock is bounded by lock_timeout. Existing rows
-- read as "no decision".
SET LOCAL lock_timeout = '5s';

ALTER TABLE brokerage_agreement_versions
    ADD COLUMN activation_decision VARCHAR(10),
    ADD COLUMN activation_decided_by VARCHAR(26),
    ADD COLUMN activation_decided_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN activation_statement_version VARCHAR(40),
    ADD COLUMN signed_document_number VARCHAR(50),
    ADD COLUMN signed_document_date DATE,
    ADD COLUMN sunvibe_signatory_name VARCHAR(100),
    ADD COLUMN sunvibe_signatory_title VARCHAR(100),
    ADD COLUMN activation_refusal_type VARCHAR(30),
    ADD COLUMN activation_refusal_note VARCHAR(500),
    -- Either no decision at all, or a decision with its manager and time on a version its owner confirmed: an
    -- activation holds its statement version and the four document columns and no refusal column, on a version
    -- activated or past it; a refusal holds its type and no statement or document column, on an ACTIVATION_REFUSED
    -- version. A CHECK passes when it yields NULL, so each branch tests its columns IS NULL / IS NOT NULL first.
    ADD CONSTRAINT ck_brokerage_agreement_versions_activation
        CHECK ((activation_decision IS NULL AND activation_decided_by IS NULL AND activation_decided_at IS NULL
                AND activation_statement_version IS NULL AND signed_document_number IS NULL
                AND signed_document_date IS NULL AND sunvibe_signatory_name IS NULL
                AND sunvibe_signatory_title IS NULL AND activation_refusal_type IS NULL
                AND activation_refusal_note IS NULL)
            OR (activation_decision IS NOT NULL AND activation_decided_by IS NOT NULL
                AND activation_decided_at IS NOT NULL AND owner_answer IS NOT NULL AND owner_answer = 'CONFIRMED'
                AND ((activation_decision = 'ACTIVATED' AND activation_statement_version IS NOT NULL
                        AND signed_document_number IS NOT NULL AND signed_document_date IS NOT NULL
                        AND sunvibe_signatory_name IS NOT NULL AND sunvibe_signatory_title IS NOT NULL
                        AND activation_refusal_type IS NULL AND activation_refusal_note IS NULL
                        AND status IN ('ACTIVATED', 'EFFECTIVE', 'SUPERSEDED', 'CANCELLED'))
                    OR (activation_decision = 'REFUSED' AND activation_refusal_type IS NOT NULL
                        AND activation_statement_version IS NULL AND signed_document_number IS NULL
                        AND signed_document_date IS NULL AND sunvibe_signatory_name IS NULL
                        AND sunvibe_signatory_title IS NULL AND status = 'ACTIVATION_REFUSED'))))
        NOT VALID;
