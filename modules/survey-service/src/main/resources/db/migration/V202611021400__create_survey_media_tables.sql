-- Survey photos of long-term checklist drafts: photos, their variants and media sets, processing and deletion jobs,
-- intake health, incidents, upload body slots, read budgets and full-listing runs (survey-media spec 4.2, 5.2, A-3,
-- A-17, A-18, A-23, A-26, A-27; survey-media brief section 19).
-- Expand only: nine new, empty tables; no existing table, column or row changes, so the previous server version keeps
-- running. The foreign keys to the populated survey_checklists take a short SHARE ROW EXCLUSIVE lock, bounded by the
-- lock timeout below (a timeout fails the start-up; the runbook retries). No foreign key leaves the module: user
-- columns name users of auth-service (ADR-009); the lead and visit of a photo are reached through its checklist.
-- Every CHECK is NULL-safe: a CHECK passes on NULL, so each disjunct tests IS [NOT] NULL before comparing, or uses
-- IS [NOT] DISTINCT FROM. Constraint names are chk_survey_media_<rule>, one rule each, at most 63 bytes.
-- Held (spec A-23) is defined once: held_at IS NOT NULL AND hold_released_at IS NULL.
-- Times are application-clock values; the seeded health row's time from SQL now() is the one exception (spec A-18).
-- Partial-index predicates are repeated as literals by the queries that use them (spec 5.2).
-- Repeatable: IF NOT EXISTS and ON CONFLICT DO NOTHING make a second run change nothing.
SET LOCAL lock_timeout = '5s';

-- The photos of one checklist: set_version grows with every change a reader can see; last_media_no numbers photos
CREATE TABLE IF NOT EXISTS survey_media_sets (
    checklist_id VARCHAR(26) NOT NULL,
    set_version BIGINT NOT NULL,
    last_media_no INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_media_sets_pkey PRIMARY KEY (checklist_id),
    CONSTRAINT fk_survey_media_sets_checklist_id FOREIGN KEY (checklist_id) REFERENCES survey_checklists (id),
    CONSTRAINT chk_survey_media_sets_set_version CHECK (set_version >= 0),
    CONSTRAINT chk_survey_media_sets_last_media_no CHECK (last_media_no >= 0)
);

-- One uploaded file of one visit, uploaded into one checklist (ULID id, ADR-006); placement, category, uploader and
-- declared hash never change (spec I-1, A-8); removal and hold only add marks (spec 4.7, A-23)
CREATE TABLE IF NOT EXISTS survey_media (
    id VARCHAR(26) NOT NULL,
    checklist_id VARCHAR(26) NOT NULL,
    upload_key VARCHAR(26) NOT NULL,
    media_no INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    space_id VARCHAR(26),
    space_code_at_upload VARCHAR(20),
    category VARCHAR(20) NOT NULL,
    caption VARCHAR(200),
    uploaded_by_user_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_reason VARCHAR(30),
    receive_token VARCHAR(26),
    receive_key VARCHAR(120),
    receive_started_at TIMESTAMP WITH TIME ZONE,
    content_received_at TIMESTAMP WITH TIME ZONE,
    source_storage_key VARCHAR(120),
    source_deleted_at TIMESTAMP WITH TIME ZONE,
    source_format VARCHAR(10),
    ready_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    removed_at TIMESTAMP WITH TIME ZONE,
    removed_by_user_id VARCHAR(26),
    removal_kind VARCHAR(10),
    removal_correction_reason VARCHAR(500),
    removal_correction_basis_on DATE,
    removal_correction_basis_text VARCHAR(500),
    removed_by_save_id BIGINT,
    held_at TIMESTAMP WITH TIME ZONE,
    held_by_user_id VARCHAR(26),
    hold_reason VARCHAR(30),
    hold_released_at TIMESTAMP WITH TIME ZONE,
    hold_released_by_user_id VARCHAR(26),
    content_delete_requested_at TIMESTAMP WITH TIME ZONE,
    content_deleted_at TIMESTAMP WITH TIME ZONE,
    content_missing_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT survey_media_pkey PRIMARY KEY (id),
    CONSTRAINT fk_survey_media_checklist_id FOREIGN KEY (checklist_id) REFERENCES survey_checklists (id),
    -- the set row is inserted first, by the photo's create (spec 4.4 step 1)
    CONSTRAINT fk_survey_media_set FOREIGN KEY (checklist_id) REFERENCES survey_media_sets (checklist_id),
    CONSTRAINT uq_survey_media_checklist_media_no UNIQUE (checklist_id, media_no),
    -- spec A-5: one photo per upload key of one uploader on one checklist
    CONSTRAINT uq_survey_media_checklist_uploader_key UNIQUE (checklist_id, uploaded_by_user_id, upload_key),
    CONSTRAINT chk_survey_media_status CHECK (status IN ('AWAITING_CONTENT', 'RECEIVING', 'PROCESSING', 'READY',
        'REJECTED', 'FAILED', 'CANCELLED', 'UPLOAD_EXPIRED')),
    CONSTRAINT chk_survey_media_category CHECK (category IN ('EXTERIOR_ACCESS', 'MAIN_SPACE', 'KITCHEN', 'BATHROOM',
        'SHARED_KITCHEN', 'SHARED_BATHROOM', 'SHARED_AMENITY', 'DETAIL')),
    CONSTRAINT chk_survey_media_source_format CHECK (source_format IN ('JPEG', 'PNG', 'WEBP', 'HEIF')),
    CONSTRAINT chk_survey_media_removal_kind CHECK (removal_kind IN ('HOLDER', 'CASCADE')),
    CONSTRAINT chk_survey_media_hold_reason CHECK (hold_reason IN ('SECURITY_INCIDENT', 'CREDENTIAL_EXPOSURE')),
    CONSTRAINT chk_survey_media_media_no CHECK (media_no > 0),
    CONSTRAINT chk_survey_media_version CHECK (version >= 0),
    -- spec A-2: 20 MiB per file
    CONSTRAINT chk_survey_media_declared_size CHECK (declared_size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT chk_survey_media_source_sha256 CHECK (source_sha256 ~ '^[0-9a-f]{64}$'),
    -- spec 5.2: each final status with exactly its reasons, every other status without one
    CONSTRAINT chk_survey_media_status_reason CHECK (
        (status = 'REJECTED' AND status_reason IS NOT NULL AND status_reason IN ('UNSUPPORTED_FORMAT', 'UNREADABLE',
            'ANIMATED', 'MOTION_PHOTO', 'COMPOSITE_FILE', 'TOO_MANY_PIXELS', 'MALWARE_DETECTED',
            'SCAN_LIMIT_EXCEEDED'))
        OR (status = 'FAILED' AND status_reason IS NOT NULL AND status_reason = 'PROCESSING_UNAVAILABLE')
        OR (status = 'CANCELLED' AND status_reason IS NOT NULL AND status_reason = 'REMOVED'
            AND removed_at IS NOT NULL)
        OR (status = 'UPLOAD_EXPIRED' AND status_reason IS NOT NULL AND status_reason = 'CONTENT_NOT_RECEIVED')
        OR (status IN ('AWAITING_CONTENT', 'RECEIVING', 'PROCESSING', 'READY') AND status_reason IS NULL)),
    -- lifecycle (spec 4.2): the claim exists exactly while RECEIVING
    CONSTRAINT chk_survey_media_receive CHECK (
        (status = 'RECEIVING' AND receive_token IS NOT NULL AND receive_key IS NOT NULL
            AND receive_started_at IS NOT NULL)
        OR (status <> 'RECEIVING' AND receive_token IS NULL AND receive_key IS NULL AND receive_started_at IS NULL)),
    CONSTRAINT chk_survey_media_content_received CHECK ((content_received_at IS NOT NULL)
        = (status IN ('PROCESSING', 'READY', 'REJECTED', 'FAILED', 'CANCELLED'))),
    CONSTRAINT chk_survey_media_source_key CHECK ((source_storage_key IS NULL) = (content_received_at IS NULL)),
    CONSTRAINT chk_survey_media_source_deleted CHECK (source_deleted_at IS NULL OR source_storage_key IS NOT NULL),
    CONSTRAINT chk_survey_media_ready CHECK ((status = 'READY') = (ready_at IS NOT NULL)),
    CONSTRAINT chk_survey_media_ready_format CHECK (status <> 'READY' OR source_format IS NOT NULL),
    CONSTRAINT chk_survey_media_finished CHECK ((finished_at IS NOT NULL)
        = (status IN ('READY', 'REJECTED', 'FAILED', 'CANCELLED', 'UPLOAD_EXPIRED'))),
    -- placement (spec 4.6, A-12): asset-only categories on the asset, space-only ones on a space, DETAIL on either
    CONSTRAINT chk_survey_media_placement CHECK (
        (category IN ('EXTERIOR_ACCESS', 'SHARED_KITCHEN', 'SHARED_BATHROOM', 'SHARED_AMENITY') AND space_id IS NULL)
        OR (category IN ('MAIN_SPACE', 'KITCHEN', 'BATHROOM') AND space_id IS NOT NULL)
        OR category = 'DETAIL'),
    CONSTRAINT chk_survey_media_space_code CHECK ((space_id IS NULL) = (space_code_at_upload IS NULL)),
    -- removal (spec 4.7, A-9): who and how, the correction trio all set or all empty and only on a removal
    CONSTRAINT chk_survey_media_removed_by CHECK ((removed_at IS NULL) = (removed_by_user_id IS NULL)),
    CONSTRAINT chk_survey_media_removal_kind_set CHECK ((removed_at IS NULL) = (removal_kind IS NULL)),
    CONSTRAINT chk_survey_media_removal_correction CHECK (
        (removal_correction_reason IS NULL AND removal_correction_basis_on IS NULL
            AND removal_correction_basis_text IS NULL)
        OR (removal_correction_reason IS NOT NULL AND removal_correction_basis_on IS NOT NULL
            AND removal_correction_basis_text IS NOT NULL AND removed_at IS NOT NULL)),
    CONSTRAINT chk_survey_media_removed_by_save CHECK ((removed_by_save_id IS NOT NULL)
        = (removal_kind IS NOT DISTINCT FROM 'CASCADE')),
    -- a cascade's correction is the G1 save's own (spec A-9)
    CONSTRAINT chk_survey_media_cascade_correction CHECK (removal_kind IS DISTINCT FROM 'CASCADE'
        OR removal_correction_reason IS NULL),
    -- hold (spec A-23): a release sets its pair at or after the hold and nulls nothing
    CONSTRAINT chk_survey_media_held_by CHECK ((held_at IS NULL) = (held_by_user_id IS NULL)),
    CONSTRAINT chk_survey_media_hold_reason_set CHECK ((held_at IS NULL) = (hold_reason IS NULL)),
    CONSTRAINT chk_survey_media_hold_released_by CHECK ((hold_released_at IS NULL)
        = (hold_released_by_user_id IS NULL)),
    CONSTRAINT chk_survey_media_hold_release_after CHECK (hold_released_at IS NULL
        OR (held_at IS NOT NULL AND hold_released_at >= held_at)),
    CONSTRAINT chk_survey_media_hold_content CHECK (held_at IS NULL OR content_received_at IS NOT NULL),
    -- deletion (spec 4.7, A-23): requested exactly for a removed READY photo that is not held
    CONSTRAINT chk_survey_media_delete_request CHECK ((content_delete_requested_at IS NOT NULL)
        = (removed_at IS NOT NULL AND status = 'READY' AND NOT (held_at IS NOT NULL AND hold_released_at IS NULL))),
    CONSTRAINT chk_survey_media_content_deleted CHECK (content_deleted_at IS NULL
        OR content_delete_requested_at IS NOT NULL),
    CONSTRAINT chk_survey_media_content_missing CHECK (content_missing_at IS NULL OR status = 'READY')
);

-- spec A-16 (a) backstop: one selected photo per content hash in a checklist
CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_media_selected_sha256 ON survey_media (checklist_id, source_sha256)
    WHERE status IN ('AWAITING_CONTENT', 'RECEIVING', 'PROCESSING', 'READY') AND removed_at IS NULL;
-- spec A-16 (b): the same hash on another visit of the lead, through survey_checklists.lead_id
CREATE INDEX IF NOT EXISTS idx_survey_media_source_sha256 ON survey_media (source_sha256);
-- spec A-3: hourly records and bytes of an uploader
CREATE INDEX IF NOT EXISTS idx_survey_media_uploader_created ON survey_media (uploaded_by_user_id, created_at);
-- spec 4.4 step 4: the sweeper's expiry, stale-claim, processing cut-off, source and clean-up queries
CREATE INDEX IF NOT EXISTS idx_survey_media_awaiting_created ON survey_media (created_at)
    WHERE status = 'AWAITING_CONTENT';
CREATE INDEX IF NOT EXISTS idx_survey_media_receiving_started ON survey_media (receive_started_at)
    WHERE status = 'RECEIVING';
CREATE INDEX IF NOT EXISTS idx_survey_media_processing_received ON survey_media (content_received_at)
    WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_survey_media_source_pending ON survey_media (content_received_at)
    WHERE source_storage_key IS NOT NULL AND source_deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_survey_media_deletion_due ON survey_media (content_delete_requested_at)
    WHERE content_delete_requested_at IS NOT NULL AND content_deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_survey_media_expired_finished ON survey_media (finished_at)
    WHERE status = 'UPLOAD_EXPIRED';

-- The three processed JPEG variants of a READY photo; insert-only, each key written once (spec I-3, A-4, A-28)
CREATE TABLE IF NOT EXISTS survey_media_variants (
    media_id VARCHAR(26) NOT NULL,
    variant VARCHAR(10) NOT NULL,
    attempt INTEGER NOT NULL,
    storage_key VARCHAR(120) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    processor_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_media_variants_pkey PRIMARY KEY (media_id, variant),
    CONSTRAINT fk_survey_media_variants_media_id FOREIGN KEY (media_id) REFERENCES survey_media (id),
    CONSTRAINT uq_survey_media_variants_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_survey_media_variants_variant CHECK (variant IN ('FULL', 'PREVIEW', 'THUMBNAIL')),
    CONSTRAINT chk_survey_media_variants_attempt CHECK (attempt > 0),
    CONSTRAINT chk_survey_media_variants_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- spec A-28: FULL at most 12 MiB, PREVIEW 2 MiB, THUMBNAIL 256 KiB
    CONSTRAINT chk_survey_media_variants_size_bytes CHECK (size_bytes > 0 AND size_bytes <= CASE variant
        WHEN 'FULL' THEN 12582912 WHEN 'PREVIEW' THEN 2097152 ELSE 262144 END),
    CONSTRAINT chk_survey_media_variants_width CHECK (width > 0),
    CONSTRAINT chk_survey_media_variants_height CHECK (height > 0),
    -- spec A-28: the deployment's processing image digest or dev-<libvips version>
    CONSTRAINT chk_survey_media_variants_processor_version CHECK (
        processor_version ~ '^[A-Za-z0-9][A-Za-z0-9._:@/+-]{0,99}$')
);

-- Processing and content-deletion work of a photo; claimed through the partial index (spec 4.4 step 3, A-7)
CREATE TABLE IF NOT EXISTS survey_media_jobs (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY,
    media_id VARCHAR(26) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    state VARCHAR(10) NOT NULL,
    attempts INTEGER NOT NULL,
    limit_hits INTEGER NOT NULL,
    lease_token VARCHAR(26),
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error_code VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_media_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT fk_survey_media_jobs_media_id FOREIGN KEY (media_id) REFERENCES survey_media (id),
    CONSTRAINT uq_survey_media_jobs_media_kind UNIQUE (media_id, kind),
    CONSTRAINT chk_survey_media_jobs_kind CHECK (kind IN ('PROCESS', 'DELETE_CONTENT')),
    CONSTRAINT chk_survey_media_jobs_state CHECK (state IN ('PENDING', 'DONE', 'CANCELLED')),
    CONSTRAINT chk_survey_media_jobs_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_survey_media_jobs_limit_hits CHECK (limit_hits >= 0)
);

CREATE INDEX IF NOT EXISTS idx_survey_media_jobs_pending_kind_due ON survey_media_jobs (kind, due_at)
    WHERE state = 'PENDING';

-- Intake health of every instance (spec A-18): one row, read without a lock, written only in its own short
-- transaction; seeded paused, so intake opens only after the first complete sweep
CREATE TABLE IF NOT EXISTS survey_media_health (
    id SMALLINT NOT NULL,
    failure_seq BIGINT NOT NULL DEFAULT 0,
    state_epoch BIGINT NOT NULL DEFAULT 0,
    last_sweep_ok_at TIMESTAMP WITH TIME ZONE,
    last_probe_ok_at TIMESTAMP WITH TIME ZONE,
    last_failure_at TIMESTAMP WITH TIME ZONE,
    last_worker_beat_at TIMESTAMP WITH TIME ZONE,
    last_deletion_beat_at TIMESTAMP WITH TIME ZONE,
    scan_blocked_since TIMESTAMP WITH TIME ZONE,
    intake_paused_since TIMESTAMP WITH TIME ZONE,
    pause_reason VARCHAR(30),
    CONSTRAINT survey_media_health_pkey PRIMARY KEY (id),
    CONSTRAINT chk_survey_media_health_id CHECK (id = 1),
    CONSTRAINT chk_survey_media_health_pause_reason CHECK (pause_reason IN ('NOT_STARTED', 'STORE', 'SCANNER',
        'PROCESSOR', 'SWEEP_STALE', 'WORKER_STALE', 'DELETION_STALE', 'PROCESSING_OFF', 'DELETION_OVERDUE')),
    CONSTRAINT chk_survey_media_health_pause CHECK ((intake_paused_since IS NULL) = (pause_reason IS NULL))
);

INSERT INTO survey_media_health (id, failure_seq, state_epoch, intake_paused_since, pause_reason)
VALUES (1, 0, 0, now(), 'NOT_STARTED')
ON CONFLICT DO NOTHING;

-- Overdue deletions (spec A-18, BR-18 "bản ghi sự cố tối thiểu"): ids and counts only, no key or name
CREATE TABLE IF NOT EXISTS survey_media_incidents (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY,
    kind VARCHAR(30) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    first_overdue_at TIMESTAMP WITH TIME ZONE NOT NULL,
    media_count INTEGER NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT survey_media_incidents_pkey PRIMARY KEY (id),
    CONSTRAINT chk_survey_media_incidents_kind CHECK (kind IN ('SOURCE_OVERDUE', 'CONTENT_DELETION_OVERDUE',
        'ORPHAN_OVERDUE')),
    CONSTRAINT chk_survey_media_incidents_media_count CHECK (media_count > 0),
    CONSTRAINT chk_survey_media_incidents_resolved CHECK (resolved_at IS NULL OR resolved_at >= opened_at)
);

-- the target of the ON CONFLICT upsert: one open incident per kind
CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_media_incidents_open_kind ON survey_media_incidents (kind)
    WHERE resolved_at IS NULL;

-- Content bodies in flight per user across instances (spec A-3); expired rows are ignored and swept
CREATE TABLE IF NOT EXISTS survey_media_body_slots (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY,
    user_id VARCHAR(26) NOT NULL,
    instance_id VARCHAR(64) NOT NULL,
    taken_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT survey_media_body_slots_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_survey_media_body_slots_user_expires ON survey_media_body_slots (user_id, expires_at);

-- Per-user hourly counters (spec A-26, A-27): read budgets, expensive outcomes and processing milliseconds
CREATE TABLE IF NOT EXISTS survey_media_read_budgets (
    user_id VARCHAR(26) NOT NULL,
    hour_start TIMESTAMP WITH TIME ZONE NOT NULL,
    kind VARCHAR(10) NOT NULL,
    read_count INTEGER NOT NULL,
    exceeded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT survey_media_read_budgets_pkey PRIMARY KEY (user_id, hour_start, kind),
    CONSTRAINT chk_survey_media_read_budgets_kind CHECK (kind IN ('FULL', 'PREVIEW', 'THUMBNAIL', 'LIST',
        'EXPENSIVE', 'PROC_MS')),
    CONSTRAINT chk_survey_media_read_budgets_read_count CHECK (read_count >= 0)
);

-- The leased, resumable full listing of the media bucket (spec 4.4 step 4)
CREATE TABLE IF NOT EXISTS survey_media_listing_runs (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_token VARCHAR(26),
    lease_until TIMESTAMP WITH TIME ZONE,
    start_after_key VARCHAR(120),
    keys_seen BIGINT NOT NULL,
    undeleted_count INTEGER NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT survey_media_listing_runs_pkey PRIMARY KEY (id),
    CONSTRAINT chk_survey_media_listing_runs_lease CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CONSTRAINT chk_survey_media_listing_runs_finished CHECK (finished_at IS NULL
        OR (finished_at >= started_at AND lease_token IS NULL)),
    CONSTRAINT chk_survey_media_listing_runs_keys_seen CHECK (keys_seen >= 0),
    CONSTRAINT chk_survey_media_listing_runs_undeleted CHECK (undeleted_count >= 0)
);

-- one open run at a time
CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_media_listing_runs_one_open ON survey_media_listing_runs ((1))
    WHERE finished_at IS NULL;
