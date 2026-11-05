-- Requalification of a PASSED revision (property-create requalification spec 4, 5.2, A-3, D5-1; property-create brief
-- US-8, BR-17).
-- Expand only: survey_checklists gets one nullable column (a metadata change, no row is rewritten; old rows keep it
-- NULL) and survey_checklist_requalifications appears empty. The new CHECK is added NOT VALID, so nothing here scans the
-- populated rows, and the next migration validates it under a SHARE UPDATE EXCLUSIVE lock. The ACCESS EXCLUSIVE lock of
-- the ALTER is held to the end of this file, which does no slow work; a timeout fails the start-up and the runbook
-- retries. Stop every old instance first: native `select *` on survey_checklists can raise `cached plan must not change
-- result type` after ADD COLUMN (not reproduced; spec 5.2, SV-090).
-- requalified_at records a fact about use, not revision content (spec A-3, I-2): a partial index cannot read another
-- table, so the mark lives on the row and its reason and source in their own row. The row is insert-only and a mark is
-- never removed. The reason, source and scope are private survey data (survey BR-4): never in History, logs or lists.
-- No foreign key leaves the module: marked_by_user_id names a user of auth-service (ADR-009). The primary key serves the
-- foreign key on checklist_id.
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_checklists
    ADD COLUMN requalified_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT chk_survey_checklists_requalified_status CHECK (requalified_at IS NULL OR status = 'PASSED')
        NOT VALID;

CREATE TABLE survey_checklist_requalifications (
    checklist_id VARCHAR(26) NOT NULL,
    reason_kind VARCHAR(20) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    event_source VARCHAR(500) NOT NULL,
    scope VARCHAR(500) NOT NULL,
    basis_date DATE NOT NULL,
    marked_by_user_id VARCHAR(26) NOT NULL,
    marked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lead_status_before VARCHAR(30) NOT NULL,
    lead_status_after VARCHAR(30) NOT NULL,
    CONSTRAINT survey_checklist_requalifications_pkey PRIMARY KEY (checklist_id),
    CONSTRAINT fk_survey_checklist_requalifications_checklist_id FOREIGN KEY (checklist_id)
        REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_checklist_requalifications_reason_kind CHECK (reason_kind IN ('WRONG_ASSET', 'WRONG_OWNER',
        'WRONG_PERSON', 'WRONG_SCOPE', 'NEED_CHANGED', 'PHOTO_PROBLEM', 'HAZARD', 'OTHER'))
);
