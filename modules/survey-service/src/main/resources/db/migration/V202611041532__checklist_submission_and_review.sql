-- Submission, review and revisions of a survey checklist (survey-checklist submission spec 4.3, 4.6, 5.3, A-1, A-2;
-- survey-checklist brief US-2, US-3, D3-3, D3-4; survey-media brief BR-20, BR-21 b).
-- Expand only: survey_checklists gets a wider status and four nullable columns (old rows keep them NULL and stay
-- DRAFT), and five new, empty tables appear; no row is rewritten, so the previous server version keeps running until
-- the first submit (spec 7.4). The widened CHECKs are exact supersets of the old ones, under the same names; new and
-- widened constraints on populated tables are added NOT VALID, so nothing here scans their rows, and the next migration
-- validates them under a SHARE UPDATE EXCLUSIVE lock. The status type change rebuilds the small partial index
-- uq_survey_checklists_draft_per_appointment (one row per visit) under the statement's lock. One transaction, bounded
-- by the lock timeout (a timeout fails the start-up; the runbook retries). No foreign key leaves the module: user columns
-- name users of auth-service (ADR-009).
-- One survey_checklists row stays one revision (spec A-1): its group rows are the submitted content, and no command
-- writes them once the status leaves DRAFT (I-2). submitted_at and decided_at follow the status, so a revision cannot
-- be SUBMITTED without its submitter or decided without a time.
-- survey_checklist_submissions, _evidence, _decisions and _evidence_rejections are insert-only (spec I-2, A-5).
-- survey_checklist_media_selections holds carried photos only (spec 5.3, A-12): an own photo keeps the media v1 rows;
-- a deselection marks the row and never touches the photo. The review reason, notes, limitations and photo marks are
-- private survey data (survey BR-4, BR-14): never in History, a close reason, logs or list views.
-- Repeatable: IF NOT EXISTS and DROP CONSTRAINT IF EXISTS make a second run change nothing.
SET LOCAL lock_timeout = '5s';

ALTER TABLE survey_checklists
    DROP CONSTRAINT IF EXISTS chk_survey_checklists_status,
    DROP CONSTRAINT IF EXISTS fk_survey_checklists_previous_checklist_id,
    DROP CONSTRAINT IF EXISTS chk_survey_checklists_submitted_pair,
    DROP CONSTRAINT IF EXISTS chk_survey_checklists_submitted_status,
    DROP CONSTRAINT IF EXISTS chk_survey_checklists_decided_status,
    ALTER COLUMN status TYPE VARCHAR(16),
    ADD COLUMN IF NOT EXISTS previous_checklist_id VARCHAR(26),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS submitted_by_user_id VARCHAR(26),
    ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT chk_survey_checklists_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'PASSED', 'NEEDS_CHANGES',
        'FAILED')) NOT VALID,
    ADD CONSTRAINT fk_survey_checklists_previous_checklist_id FOREIGN KEY (previous_checklist_id)
        REFERENCES survey_checklists (id) NOT VALID,
    ADD CONSTRAINT chk_survey_checklists_submitted_pair CHECK ((submitted_at IS NULL)
        = (submitted_by_user_id IS NULL)) NOT VALID,
    ADD CONSTRAINT chk_survey_checklists_submitted_status CHECK ((status = 'DRAFT') = (submitted_at IS NULL)) NOT VALID,
    ADD CONSTRAINT chk_survey_checklists_decided_status CHECK ((status IN ('PASSED', 'NEEDS_CHANGES', 'FAILED'))
        = (decided_at IS NOT NULL)) NOT VALID;

-- spec 4.3: the facts of one submission, one row per revision; the visit as it stood (BR-13), the G1 scope, property
-- type and spaces, the group versions and the hash of the canonical content and evidence (plan PD-1). The visit id is
-- a copy of the revision's own, which already holds the foreign key. property_type stays NULL for a type recorded as
-- not applicable.
CREATE TABLE IF NOT EXISTS survey_checklist_submissions (
    checklist_id VARCHAR(26) NOT NULL,
    submitted_by_user_id VARCHAR(26) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    template_code VARCHAR(30) NOT NULL,
    template_version INTEGER NOT NULL,
    kind VARCHAR(20) NOT NULL,
    lead_intent VARCHAR(20) NOT NULL,
    intent_cycle INTEGER NOT NULL,
    visit_appointment_id VARCHAR(26) NOT NULL,
    visit_performed_by VARCHAR(26) NOT NULL,
    visit_arrived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    visit_recorded_by VARCHAR(26) NOT NULL,
    visit_recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rental_scope VARCHAR(10) NOT NULL,
    property_type VARCHAR(30),
    spaces TEXT NOT NULL,
    group_versions TEXT NOT NULL,
    media_policy VARCHAR(30) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    CONSTRAINT survey_checklist_submissions_pkey PRIMARY KEY (checklist_id),
    CONSTRAINT fk_survey_checklist_submissions_checklist_id FOREIGN KEY (checklist_id)
        REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_checklist_submissions_rental_scope CHECK (rental_scope IN ('WHOLE', 'PARTS')),
    CONSTRAINT chk_survey_checklist_submissions_spaces CHECK (spaces IS JSON ARRAY),
    CONSTRAINT chk_survey_checklist_submissions_group_versions CHECK (group_versions IS JSON OBJECT),
    -- survey-checklist D3-1: the photo rules this record was made under
    CONSTRAINT chk_survey_checklist_submissions_media_policy CHECK (media_policy = 'MEDIA_V1_NO_PERMISSION'),
    CONSTRAINT chk_survey_checklist_submissions_content_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$')
);

-- spec 4.3: every photo selected in the revision (own or carried) with its FULL hash; a row here keeps the photo's
-- content for good (spec I-5, survey-checklist D3-3)
CREATE TABLE IF NOT EXISTS survey_checklist_evidence (
    checklist_id VARCHAR(26) NOT NULL,
    media_id VARCHAR(26) NOT NULL,
    category VARCHAR(20) NOT NULL,
    space_id VARCHAR(26),
    space_code VARCHAR(20),
    full_sha256 VARCHAR(64) NOT NULL,
    CONSTRAINT survey_checklist_evidence_pkey PRIMARY KEY (checklist_id, media_id),
    CONSTRAINT fk_survey_checklist_evidence_checklist_id FOREIGN KEY (checklist_id)
        REFERENCES survey_checklist_submissions (checklist_id),
    CONSTRAINT fk_survey_checklist_evidence_media_id FOREIGN KEY (media_id) REFERENCES survey_media (id),
    CONSTRAINT chk_survey_checklist_evidence_category CHECK (category IN ('EXTERIOR_ACCESS', 'MAIN_SPACE', 'KITCHEN',
        'BATHROOM', 'SHARED_KITCHEN', 'SHARED_BATHROOM', 'SHARED_AMENITY', 'DETAIL')),
    CONSTRAINT chk_survey_checklist_evidence_space CHECK ((space_id IS NULL) = (space_code IS NULL)),
    CONSTRAINT chk_survey_checklist_evidence_full_sha256 CHECK (full_sha256 ~ '^[0-9a-f]{64}$')
);

-- the foreign key, and the reference check of a removal or a deletion (spec I-5)
CREATE INDEX IF NOT EXISTS idx_survey_checklist_evidence_media_id ON survey_checklist_evidence (media_id);

-- spec 4.5: one decision per revision; kinds follow the outcome, a reason with both returns, limitations only on PASSED
CREATE TABLE IF NOT EXISTS survey_checklist_decisions (
    checklist_id VARCHAR(26) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    needs_changes_kind VARCHAR(20),
    failed_kind VARCHAR(16),
    reason VARCHAR(2000),
    notes VARCHAR(2000),
    limitations VARCHAR(2000),
    reviewer_user_id VARCHAR(26) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lead_status_before VARCHAR(30) NOT NULL,
    lead_status_after VARCHAR(30) NOT NULL,
    CONSTRAINT survey_checklist_decisions_pkey PRIMARY KEY (checklist_id),
    CONSTRAINT fk_survey_checklist_decisions_checklist_id FOREIGN KEY (checklist_id)
        REFERENCES survey_checklist_submissions (checklist_id),
    CONSTRAINT chk_survey_checklist_decisions_outcome CHECK (outcome IN ('PASSED', 'NEEDS_CHANGES', 'FAILED')),
    CONSTRAINT chk_survey_checklist_decisions_needs_changes_kind CHECK (needs_changes_kind IN ('DOCUMENTS_ONLY',
        'RESURVEY_REQUIRED')),
    CONSTRAINT chk_survey_checklist_decisions_failed_kind CHECK (failed_kind IN ('OUT_OF_SCOPE', 'NOT_ACCEPTED',
        'TEMPORARY')),
    CONSTRAINT chk_survey_checklist_decisions_needs_changes CHECK ((outcome = 'NEEDS_CHANGES')
        = (needs_changes_kind IS NOT NULL)),
    CONSTRAINT chk_survey_checklist_decisions_failed CHECK ((outcome = 'FAILED') = (failed_kind IS NOT NULL)),
    CONSTRAINT chk_survey_checklist_decisions_reason CHECK (outcome = 'PASSED' OR reason IS NOT NULL),
    CONSTRAINT chk_survey_checklist_decisions_limitations CHECK (outcome = 'PASSED' OR limitations IS NULL)
);

-- spec A-5: the frozen photos the reviewer did not accept, each with a reason; every other one counts as accepted
CREATE TABLE IF NOT EXISTS survey_checklist_evidence_rejections (
    checklist_id VARCHAR(26) NOT NULL,
    media_id VARCHAR(26) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    CONSTRAINT survey_checklist_evidence_rejections_pkey PRIMARY KEY (checklist_id, media_id),
    CONSTRAINT fk_survey_checklist_evidence_rejections_decision FOREIGN KEY (checklist_id)
        REFERENCES survey_checklist_decisions (checklist_id),
    CONSTRAINT fk_survey_checklist_evidence_rejections_evidence FOREIGN KEY (checklist_id, media_id)
        REFERENCES survey_checklist_evidence (checklist_id, media_id)
);

-- spec 4.6, A-12: a photo carried into a later revision of the visit. A deselection by the holder keeps its own
-- correction, as a removal does; a G1 cascade names its save, whose row keeps the correction (survey-media spec A-9).
CREATE TABLE IF NOT EXISTS survey_checklist_media_selections (
    checklist_id VARCHAR(26) NOT NULL,
    media_id VARCHAR(26) NOT NULL,
    carried_from_checklist_id VARCHAR(26) NOT NULL,
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deselected_at TIMESTAMP WITH TIME ZONE,
    deselected_by_user_id VARCHAR(26),
    deselection_kind VARCHAR(10),
    deselected_by_save_id BIGINT,
    deselection_correction_reason VARCHAR(500),
    deselection_correction_basis_on DATE,
    deselection_correction_basis_text VARCHAR(500),
    CONSTRAINT survey_checklist_media_selections_pkey PRIMARY KEY (checklist_id, media_id),
    CONSTRAINT fk_survey_checklist_media_selections_checklist_id FOREIGN KEY (checklist_id)
        REFERENCES survey_checklists (id),
    CONSTRAINT fk_survey_checklist_media_selections_media_id FOREIGN KEY (media_id) REFERENCES survey_media (id),
    CONSTRAINT fk_survey_checklist_media_selections_carried_from FOREIGN KEY (carried_from_checklist_id)
        REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_checklist_media_selections_kind CHECK (deselection_kind IN ('HOLDER', 'CASCADE')),
    CONSTRAINT chk_survey_checklist_media_selections_by CHECK ((deselected_at IS NULL)
        = (deselected_by_user_id IS NULL)),
    CONSTRAINT chk_survey_checklist_media_selections_kind_set CHECK ((deselected_at IS NULL)
        = (deselection_kind IS NULL)),
    CONSTRAINT chk_survey_checklist_media_selections_save CHECK ((deselected_by_save_id IS NOT NULL)
        = (deselection_kind IS NOT DISTINCT FROM 'CASCADE')),
    CONSTRAINT chk_survey_checklist_media_selections_correction CHECK (
        (deselection_correction_reason IS NULL AND deselection_correction_basis_on IS NULL
            AND deselection_correction_basis_text IS NULL)
        OR (deselection_correction_reason IS NOT NULL AND deselection_correction_basis_on IS NOT NULL
            AND deselection_correction_basis_text IS NOT NULL AND deselected_at IS NOT NULL)),
    CONSTRAINT chk_survey_checklist_media_selections_cascade CHECK (deselection_kind IS DISTINCT FROM 'CASCADE'
        OR deselection_correction_reason IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_survey_checklist_media_selections_media_id
    ON survey_checklist_media_selections (media_id);
CREATE INDEX IF NOT EXISTS idx_survey_checklist_media_selections_carried_from
    ON survey_checklist_media_selections (carried_from_checklist_id);

-- spec A-7, plan PD-9: takeover #1 of a revision started by someone other than the previous holder; from_user_id names
-- that holder, so chk_survey_checklist_takeovers_from_user holds unchanged
ALTER TABLE survey_checklist_takeovers
    DROP CONSTRAINT IF EXISTS chk_survey_checklist_takeovers_kind,
    ADD CONSTRAINT chk_survey_checklist_takeovers_kind CHECK (kind IN ('START_BY_OTHER', 'TAKEOVER',
        'REVISION_BY_OTHER')) NOT VALID;

-- spec 4.9: refused submits, decisions, revision starts and deselections; a decision by an excluded user
ALTER TABLE survey_security_events
    DROP CONSTRAINT IF EXISTS chk_survey_security_events_action,
    ADD CONSTRAINT chk_survey_security_events_action CHECK (action IN ('READ', 'START', 'SAVE', 'TAKEOVER', 'UPLOAD',
        'REMOVE', 'SCAN', 'HOLD', 'RELEASE', 'SUBMIT', 'REVIEW', 'REVISION', 'DESELECT')) NOT VALID,
    DROP CONSTRAINT IF EXISTS chk_survey_security_events_outcome,
    ADD CONSTRAINT chk_survey_security_events_outcome CHECK (outcome IN ('NOT_VISIBLE', 'NOT_ASSIGNEE',
        'MALWARE_DETECTED', 'HELD', 'RELEASED', 'BUDGET_EXCEEDED', 'NOT_INDEPENDENT')) NOT VALID;
