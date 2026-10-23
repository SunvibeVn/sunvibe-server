-- Survey appointments of owner leads and their histories (survey-schedule spec 5.2; survey-schedule brief US-1..US-4,
-- BR-2, BR-4..BR-9; survey module brief BR-5, BR-12).
-- Expand only: new tables, no existing table, column or row changes. No foreign key leaves the module: lead_id names
-- an owner lead of acquisition-service and the user columns name users of auth-service (ADR-009).
-- survey_appointments is the aggregate (ULID id, ADR-006). appointment_no counts the appointments of a lead. A
-- re-survey links the visit it follows and has a reason; a first survey has neither. The current editable values sit
-- on the row; every version of them is in survey_appointment_revisions. The lead's place is copied at booking (the
-- lead itself never changes). Confirmation, visit and cancellation columns are set exactly when they apply.
-- At most one SCHEDULED appointment per lead (spec I-1): partial unique index uk_survey_appointments_one_scheduled.
-- The four history tables are insert-only (survey BR-5): no version, no updated_at; Snowflake ids (BIGINT).
-- Place, notes, contacts, reasons and bases are private staff data (survey-schedule BR-5, BR-8).
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS survey_appointments (
    id VARCHAR(26) NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    appointment_no INTEGER NOT NULL,
    kind VARCHAR(10) NOT NULL,
    previous_visit_id VARCHAR(26),
    resurvey_reason VARCHAR(500),
    intent VARCHAR(20) NOT NULL,
    intent_cycle INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    meeting_address VARCHAR(255) NOT NULL,
    meeting_province_code VARCHAR(10) NOT NULL,
    meeting_province_name VARCHAR(100) NOT NULL,
    meeting_ward_code VARCHAR(10) NOT NULL,
    meeting_ward_name VARCHAR(100) NOT NULL,
    meeting_latitude NUMERIC(9, 6),
    meeting_longitude NUMERIC(9, 6),
    access_note VARCHAR(1000),
    on_site_contact_name VARCHAR(100),
    on_site_contact_role VARCHAR(100),
    current_revision_no INTEGER NOT NULL,
    lead_address_detail VARCHAR(255) NOT NULL,
    lead_province_code VARCHAR(10) NOT NULL,
    lead_province_name VARCHAR(100) NOT NULL,
    lead_ward_code VARCHAR(10) NOT NULL,
    lead_ward_name VARCHAR(100) NOT NULL,
    lead_latitude NUMERIC(9, 6),
    lead_longitude NUMERIC(9, 6),
    location_reason VARCHAR(500),
    surveyor_user_id VARCHAR(26) NOT NULL,
    confirmation_state VARCHAR(12) NOT NULL,
    confirmation_cause VARCHAR(20),
    ever_confirmed BOOLEAN NOT NULL,
    confirmation_changed_by VARCHAR(26) NOT NULL,
    confirmation_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmation_note VARCHAR(500),
    visit_record_kind VARCHAR(10),
    performed_by VARCHAR(26),
    arrived_at TIMESTAMP WITH TIME ZONE,
    visit_revision_no INTEGER,
    visit_note VARCHAR(2000),
    visit_recorded_by VARCHAR(26),
    visit_recorded_at TIMESTAMP WITH TIME ZONE,
    proxy_reason VARCHAR(500),
    basis_confirmed_on DATE,
    basis_text VARCHAR(500),
    cancel_reason VARCHAR(20),
    cancel_description VARCHAR(2000),
    cancelled_by VARCHAR(26),
    cancelled_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT survey_appointments_pkey PRIMARY KEY (id),
    CONSTRAINT uk_survey_appointments_lead_id_appointment_no UNIQUE (lead_id, appointment_no),
    CONSTRAINT fk_survey_appointments_previous_visit_id FOREIGN KEY (previous_visit_id)
        REFERENCES survey_appointments (id),
    CONSTRAINT chk_survey_appointments_appointment_no CHECK (appointment_no > 0),
    CONSTRAINT chk_survey_appointments_kind CHECK (kind IN ('FIRST', 'RESURVEY')),
    CONSTRAINT chk_survey_appointments_resurvey CHECK (
        (kind = 'RESURVEY' AND previous_visit_id IS NOT NULL AND resurvey_reason IS NOT NULL)
        OR (kind = 'FIRST' AND previous_visit_id IS NULL AND resurvey_reason IS NULL)),
    CONSTRAINT chk_survey_appointments_intent CHECK (intent IN ('SHORT_TERM_RENT', 'LONG_TERM_RENT')),
    CONSTRAINT chk_survey_appointments_intent_cycle CHECK (intent_cycle > 0),
    CONSTRAINT chk_survey_appointments_status CHECK (status IN ('SCHEDULED', 'VISITED', 'CANCELLED')),
    CONSTRAINT chk_survey_appointments_duration CHECK (
        duration_minutes BETWEEN 15 AND 480 AND duration_minutes % 15 = 0),
    CONSTRAINT chk_survey_appointments_meeting_pin CHECK ((meeting_latitude IS NULL) = (meeting_longitude IS NULL)),
    CONSTRAINT chk_survey_appointments_lead_pin CHECK ((lead_latitude IS NULL) = (lead_longitude IS NULL)),
    CONSTRAINT chk_survey_appointments_current_revision_no CHECK (current_revision_no > 0),
    CONSTRAINT chk_survey_appointments_confirmation_state CHECK (confirmation_state IN ('CONFIRMED', 'UNCONFIRMED')),
    CONSTRAINT chk_survey_appointments_confirmation_cause CHECK (confirmation_cause IN ('NEW', 'TIME_CHANGED',
        'LOCATION_CHANGED', 'REASSIGNED', 'USER')),
    CONSTRAINT chk_survey_appointments_confirmation CHECK (
        (confirmation_state = 'CONFIRMED' AND confirmation_cause IS NULL AND ever_confirmed)
        OR (confirmation_state = 'UNCONFIRMED' AND confirmation_cause IS NOT NULL)),
    CONSTRAINT chk_survey_appointments_visit CHECK (
        (status = 'VISITED' AND visit_record_kind IS NOT NULL AND performed_by IS NOT NULL AND arrived_at IS NOT NULL
            AND visit_revision_no IS NOT NULL AND visit_note IS NOT NULL AND visit_recorded_by IS NOT NULL
            AND visit_recorded_at IS NOT NULL)
        OR (status <> 'VISITED' AND visit_record_kind IS NULL AND performed_by IS NULL AND arrived_at IS NULL
            AND visit_revision_no IS NULL AND visit_note IS NULL AND visit_recorded_by IS NULL
            AND visit_recorded_at IS NULL)),
    CONSTRAINT chk_survey_appointments_visit_record_kind CHECK (visit_record_kind IN ('OWN', 'PROXY')),
    CONSTRAINT chk_survey_appointments_proxy CHECK (
        (visit_record_kind IS NOT DISTINCT FROM 'PROXY' AND proxy_reason IS NOT NULL
            AND basis_confirmed_on IS NOT NULL AND basis_text IS NOT NULL)
        OR (visit_record_kind IS DISTINCT FROM 'PROXY' AND proxy_reason IS NULL AND basis_confirmed_on IS NULL
            AND basis_text IS NULL)),
    CONSTRAINT chk_survey_appointments_own_record CHECK (
        visit_record_kind IS DISTINCT FROM 'OWN' OR performed_by = visit_recorded_by),
    CONSTRAINT chk_survey_appointments_cancellation CHECK (
        (status = 'CANCELLED' AND cancel_reason IS NOT NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancel_reason IS NULL AND cancel_description IS NULL AND cancelled_by IS NULL
            AND cancelled_at IS NULL)),
    CONSTRAINT chk_survey_appointments_cancel_reason CHECK (cancel_reason IN ('OWNER_CANCELLED', 'STAFF_CANCELLED',
        'OWNER_ABSENT', 'NO_ACCESS', 'OTHER', 'LEAD_ON_HOLD', 'LEAD_CLOSED', 'INTENT_CHANGED')),
    CONSTRAINT chk_survey_appointments_other_described CHECK (
        cancel_reason IS DISTINCT FROM 'OTHER' OR cancel_description IS NOT NULL)
);

-- spec I-1: one scheduled appointment per lead, also under concurrent bookings
CREATE UNIQUE INDEX IF NOT EXISTS uk_survey_appointments_one_scheduled
    ON survey_appointments (lead_id) WHERE status = 'SCHEDULED';
-- "My Surveys" list and the overlap check of one surveyor (survey-schedule US-4, US-1 AC-4)
CREATE INDEX IF NOT EXISTS idx_survey_appointments_surveyor_status_start
    ON survey_appointments (surveyor_user_id, status, start_at);
-- "All Surveys" list
CREATE INDEX IF NOT EXISTS idx_survey_appointments_status_start ON survey_appointments (status, start_at);
-- the current visit of a lead (spec A-9)
CREATE INDEX IF NOT EXISTS idx_survey_appointments_lead_cycle_status
    ON survey_appointments (lead_id, intent_cycle, status);
CREATE INDEX IF NOT EXISTS idx_survey_appointments_previous_visit_id ON survey_appointments (previous_visit_id);

-- Every version of the editable values of an appointment (survey-schedule US-2 AC-1): the full values, why (null
-- only for revision 1 at the lead's place), who and from when.
CREATE TABLE IF NOT EXISTS survey_appointment_revisions (
    id BIGINT NOT NULL,
    appointment_id VARCHAR(26) NOT NULL,
    revision_no INTEGER NOT NULL,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    meeting_address VARCHAR(255) NOT NULL,
    meeting_province_code VARCHAR(10) NOT NULL,
    meeting_province_name VARCHAR(100) NOT NULL,
    meeting_ward_code VARCHAR(10) NOT NULL,
    meeting_ward_name VARCHAR(100) NOT NULL,
    meeting_latitude NUMERIC(9, 6),
    meeting_longitude NUMERIC(9, 6),
    access_note VARCHAR(1000),
    on_site_contact_name VARCHAR(100),
    on_site_contact_role VARCHAR(100),
    reason VARCHAR(500),
    actor_user_id VARCHAR(26) NOT NULL,
    CONSTRAINT survey_appointment_revisions_pkey PRIMARY KEY (id),
    CONSTRAINT uk_survey_appointment_revisions_appointment_revision UNIQUE (appointment_id, revision_no),
    CONSTRAINT fk_survey_appointment_revisions_appointment_id FOREIGN KEY (appointment_id)
        REFERENCES survey_appointments (id),
    CONSTRAINT chk_survey_appointment_revisions_revision_no CHECK (revision_no > 0),
    CONSTRAINT chk_survey_appointment_revisions_duration CHECK (
        duration_minutes BETWEEN 15 AND 480 AND duration_minutes % 15 = 0),
    CONSTRAINT chk_survey_appointment_revisions_meeting_pin CHECK (
        (meeting_latitude IS NULL) = (meeting_longitude IS NULL)),
    CONSTRAINT chk_survey_appointment_revisions_reason CHECK (revision_no = 1 OR reason IS NOT NULL)
);

-- the revision in force at a minute (spec 4.8)
CREATE INDEX IF NOT EXISTS idx_survey_appointment_revisions_appointment_valid_from
    ON survey_appointment_revisions (appointment_id, valid_from);

-- What happened to an appointment, in order; BOOKED and SURVEYOR_CHANGED rows give who held it when.
CREATE TABLE IF NOT EXISTS survey_appointment_events (
    id BIGINT NOT NULL,
    appointment_id VARCHAR(26) NOT NULL,
    type VARCHAR(20) NOT NULL,
    actor_user_id VARCHAR(26),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision_no INTEGER,
    from_surveyor_id VARCHAR(26),
    to_surveyor_id VARCHAR(26),
    confirmation_cause VARCHAR(20),
    note VARCHAR(500),
    CONSTRAINT survey_appointment_events_pkey PRIMARY KEY (id),
    CONSTRAINT fk_survey_appointment_events_appointment_id FOREIGN KEY (appointment_id)
        REFERENCES survey_appointments (id),
    CONSTRAINT chk_survey_appointment_events_type CHECK (type IN ('BOOKED', 'CHANGED', 'CONFIRMED', 'UNCONFIRMED',
        'SURVEYOR_CHANGED', 'CANCELLED', 'VISITED')),
    CONSTRAINT chk_survey_appointment_events_confirmation_cause CHECK (confirmation_cause IN ('NEW', 'TIME_CHANGED',
        'LOCATION_CHANGED', 'REASSIGNED', 'USER')),
    CONSTRAINT chk_survey_appointment_events_holder CHECK (
        type NOT IN ('BOOKED', 'SURVEYOR_CHANGED') OR to_surveyor_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_survey_appointment_events_appointment_occurred_at
    ON survey_appointment_events (appointment_id, occurred_at);

-- A held lead whose survey work goes on from its current visit (survey-schedule BR-11): why, on what basis, who, when.
CREATE TABLE IF NOT EXISTS survey_resumptions (
    id BIGINT NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    visit_appointment_id VARCHAR(26) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    basis_confirmed_on DATE NOT NULL,
    basis_text VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(26) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_resumptions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_survey_resumptions_visit_appointment_id FOREIGN KEY (visit_appointment_id)
        REFERENCES survey_appointments (id)
);

CREATE INDEX IF NOT EXISTS idx_survey_resumptions_lead_occurred_at ON survey_resumptions (lead_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_survey_resumptions_visit_appointment_id ON survey_resumptions (visit_appointment_id);

-- Every time a staff member read the survey block of a lead, which holds places, access notes and on-site contacts
-- (survey-schedule BR-5, spec A-13): who, when, which lead. Separate from the lead's own access log.
CREATE TABLE IF NOT EXISTS survey_access_logs (
    id BIGINT NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    user_id VARCHAR(26) NOT NULL,
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_access_logs_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_survey_access_logs_lead_accessed_at ON survey_access_logs (lead_id, accessed_at);
CREATE INDEX IF NOT EXISTS idx_survey_access_logs_user_id ON survey_access_logs (user_id);
