-- Survey checklist drafts with their groups, takeovers, save log and security events, and the resource of survey
-- access-log rows (survey-checklist spec 5.2; survey-checklist brief BR-14, BR-15, BR-20, BR-22; survey module brief
-- BR-5, BR-11).
-- Expand only: four new tables and two nullable columns with CHECKs on survey_access_logs; no row is rewritten and no
-- existing column or constraint changes, so the previous server version keeps running. Existing access-log rows keep
-- a null resource (they are survey-block reads). No foreign key leaves the module: lead_id names an owner lead of
-- acquisition-service and the user columns name users of auth-service (ADR-009).
-- survey_checklists is the aggregate (ULID id, ADR-006): one row per checklist revision, numbered per lead, bound to
-- one VISITED appointment; written at start and at each takeover only (spec I-7). This slice has DRAFT checklists of
-- template LONG_TERM_RENT, kind FULL_ASSESSMENT; later slices widen these CHECKs. At most one DRAFT per appointment
-- (spec I-2), also under concurrent starts: the partial unique index and the (lead_id, checklist_no) unique key.
-- survey_checklist_groups holds one JSON document per group (content with per-unit metadata, spec A-3, PD-1), the
-- group's version and its last save; all seven rows are inserted at start (version 0). IS JSON OBJECT needs
-- PostgreSQL 16 or later.
-- survey_checklist_takeovers, survey_checklist_saves and survey_security_events are insert-only (survey BR-5,
-- spec I-6); Snowflake ids (BIGINT). A checkpoint (snapshot) exists only for a TAKEOVER and may be redacted later by the
-- retention job still to be decided (survey BR-11). A save carries its correction all set or all empty.
-- Checklist content, checkpoints, corrections and security events are private survey data (survey BR-4, BR-14).
-- Repeatable: IF NOT EXISTS and DROP CONSTRAINT IF EXISTS make a second run change nothing.
CREATE TABLE IF NOT EXISTS survey_checklists (
    id VARCHAR(26) NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    checklist_no INTEGER NOT NULL,
    visit_appointment_id VARCHAR(26) NOT NULL,
    intent_cycle INTEGER NOT NULL,
    holder_user_id VARCHAR(26) NOT NULL,
    template_code VARCHAR(30) NOT NULL,
    template_version INTEGER NOT NULL,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_by VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT survey_checklists_pkey PRIMARY KEY (id),
    CONSTRAINT uq_survey_checklists_lead_no UNIQUE (lead_id, checklist_no),
    CONSTRAINT fk_survey_checklists_visit_appointment_id FOREIGN KEY (visit_appointment_id)
        REFERENCES survey_appointments (id),
    CONSTRAINT chk_survey_checklists_checklist_no CHECK (checklist_no > 0),
    CONSTRAINT chk_survey_checklists_intent_cycle CHECK (intent_cycle > 0),
    CONSTRAINT chk_survey_checklists_template_code CHECK (template_code = 'LONG_TERM_RENT'),
    CONSTRAINT chk_survey_checklists_template_version CHECK (template_version > 0),
    CONSTRAINT chk_survey_checklists_kind CHECK (kind = 'FULL_ASSESSMENT'),
    CONSTRAINT chk_survey_checklists_status CHECK (status = 'DRAFT'),
    CONSTRAINT chk_survey_checklists_version CHECK (version >= 0)
);

-- spec I-2: one draft per visit, also under concurrent starts
CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_checklists_draft_per_appointment
    ON survey_checklists (visit_appointment_id) WHERE status = 'DRAFT';
CREATE INDEX IF NOT EXISTS idx_survey_checklists_visit_appointment_id ON survey_checklists (visit_appointment_id);

CREATE TABLE IF NOT EXISTS survey_checklist_groups (
    checklist_id VARCHAR(26) NOT NULL,
    group_key VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    version BIGINT NOT NULL,
    updated_by VARCHAR(26),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT survey_checklist_groups_pkey PRIMARY KEY (checklist_id, group_key),
    CONSTRAINT fk_survey_checklist_groups_checklist_id FOREIGN KEY (checklist_id) REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_checklist_groups_group_key CHECK (group_key IN ('IDENTIFICATION', 'PARTS', 'DIMENSIONS',
        'CONDITION', 'SAFETY', 'AMENITIES', 'TERMS')),
    CONSTRAINT chk_survey_checklist_groups_content CHECK (content IS JSON OBJECT),
    CONSTRAINT chk_survey_checklist_groups_version CHECK (version >= 0),
    CONSTRAINT chk_survey_checklist_groups_updated CHECK ((updated_by IS NULL) = (updated_at IS NULL))
);

CREATE TABLE IF NOT EXISTS survey_checklist_takeovers (
    id BIGINT NOT NULL,
    checklist_id VARCHAR(26) NOT NULL,
    takeover_no INTEGER NOT NULL,
    kind VARCHAR(20) NOT NULL,
    from_user_id VARCHAR(26),
    to_user_id VARCHAR(26) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    basis_confirmed_on DATE NOT NULL,
    basis_text VARCHAR(500) NOT NULL,
    snapshot TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_checklist_takeovers_pkey PRIMARY KEY (id),
    CONSTRAINT uq_survey_checklist_takeovers_checklist_no UNIQUE (checklist_id, takeover_no),
    CONSTRAINT fk_survey_checklist_takeovers_checklist_id FOREIGN KEY (checklist_id)
        REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_checklist_takeovers_takeover_no CHECK (takeover_no > 0),
    CONSTRAINT chk_survey_checklist_takeovers_kind CHECK (kind IN ('START_BY_OTHER', 'TAKEOVER')),
    CONSTRAINT chk_survey_checklist_takeovers_from_user CHECK ((kind = 'START_BY_OTHER') = (from_user_id IS NULL)),
    CONSTRAINT chk_survey_checklist_takeovers_snapshot CHECK (kind <> 'START_BY_OTHER' OR snapshot IS NULL),
    CONSTRAINT chk_survey_checklist_takeovers_snapshot_json CHECK (snapshot IS NULL OR snapshot IS JSON OBJECT)
);

CREATE TABLE IF NOT EXISTS survey_checklist_saves (
    id BIGINT NOT NULL,
    checklist_id VARCHAR(26) NOT NULL,
    group_key VARCHAR(20) NOT NULL,
    group_version BIGINT NOT NULL,
    actor_user_id VARCHAR(26) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correction_reason VARCHAR(500),
    correction_basis_confirmed_on DATE,
    correction_basis_text VARCHAR(500),
    corrected_units TEXT,
    CONSTRAINT survey_checklist_saves_pkey PRIMARY KEY (id),
    CONSTRAINT fk_survey_checklist_saves_checklist_id FOREIGN KEY (checklist_id) REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_checklist_saves_group_key CHECK (group_key IN ('IDENTIFICATION', 'PARTS', 'DIMENSIONS',
        'CONDITION', 'SAFETY', 'AMENITIES', 'TERMS')),
    CONSTRAINT chk_survey_checklist_saves_group_version CHECK (group_version > 0),
    CONSTRAINT chk_survey_checklist_saves_correction CHECK (
        (correction_reason IS NULL AND correction_basis_confirmed_on IS NULL AND correction_basis_text IS NULL
            AND corrected_units IS NULL)
        OR (correction_reason IS NOT NULL AND correction_basis_confirmed_on IS NOT NULL
            AND correction_basis_text IS NOT NULL AND corrected_units IS NOT NULL)),
    CONSTRAINT chk_survey_checklist_saves_corrected_units CHECK (corrected_units IS NULL
        OR corrected_units IS JSON ARRAY)
);

CREATE INDEX IF NOT EXISTS idx_survey_checklist_saves_checklist_saved_at
    ON survey_checklist_saves (checklist_id, saved_at);

-- survey-checklist BR-20, E-2: refused access to an existing resource at the checklist boundary; no body or content
CREATE TABLE IF NOT EXISTS survey_security_events (
    id BIGINT NOT NULL,
    user_id VARCHAR(26) NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    resource_id VARCHAR(40) NOT NULL,
    action VARCHAR(10) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(64),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_security_events_pkey PRIMARY KEY (id),
    CONSTRAINT chk_survey_security_events_resource_type CHECK (resource_type IN ('LEAD', 'APPOINTMENT', 'CHECKLIST',
        'CHECKPOINT')),
    CONSTRAINT chk_survey_security_events_action CHECK (action IN ('READ', 'START', 'SAVE', 'TAKEOVER')),
    CONSTRAINT chk_survey_security_events_outcome CHECK (outcome IN ('NOT_VISIBLE', 'NOT_ASSIGNEE'))
);

CREATE INDEX IF NOT EXISTS idx_survey_security_events_lead_occurred_at
    ON survey_security_events (lead_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_survey_security_events_user_occurred_at
    ON survey_security_events (user_id, occurred_at);

-- survey-checklist spec A-10: what a read showed; existing rows stay null (survey-block reads before the checklist)
ALTER TABLE survey_access_logs ADD COLUMN IF NOT EXISTS resource_type VARCHAR(20);
ALTER TABLE survey_access_logs ADD COLUMN IF NOT EXISTS resource_id VARCHAR(40);
ALTER TABLE survey_access_logs DROP CONSTRAINT IF EXISTS chk_survey_access_logs_resource_type;
ALTER TABLE survey_access_logs ADD CONSTRAINT chk_survey_access_logs_resource_type
    CHECK (resource_type IS NULL OR resource_type IN ('SURVEY_BLOCK', 'CHECKLIST', 'CHECKPOINT'));
ALTER TABLE survey_access_logs DROP CONSTRAINT IF EXISTS chk_survey_access_logs_resource_pair;
ALTER TABLE survey_access_logs ADD CONSTRAINT chk_survey_access_logs_resource_pair
    CHECK ((resource_type IS NULL) = (resource_id IS NULL));
