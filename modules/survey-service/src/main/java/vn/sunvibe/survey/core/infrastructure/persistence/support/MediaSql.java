package vn.sunvibe.survey.core.infrastructure.persistence.support;

/**
 * The SQL of the survey-media adapters (survey-media spec 5.2, 5.4, A-3, A-6, A-19, A-26, A-27), in one place so the
 * EXPLAIN tests check the very text the adapters run. Partial-index predicates are repeated as literals, never bound
 * parameters, so a generic plan can use the partial index. Named parameters ({@code :name}).
 */
public final class MediaSql {

    /** spec A-3: the namespace of the per-user advisory transaction lock (two-int form), "MEUS". */
    public static final int USER_LOCK_NAMESPACE = 0x4D455553;
    /** spec A-19: the namespace of the refusal-event key lock (two-int form), "MEVE". */
    public static final int EVENT_LOCK_NAMESPACE = 0x4D455645;

    private static final String REF = "SELECT id, checklist_id FROM survey_media WHERE ";

    /** spec 4.4 step 4: AWAITING_CONTENT 60 minutes after creation (partial index on created_at). */
    public static final String FIND_AWAITING_CREATED_BEFORE = REF
            + "status = 'AWAITING_CONTENT' AND created_at <= :cutoff ORDER BY created_at LIMIT :limit";
    /** spec 4.4 step 4: a RECEIVING claim older than 15 minutes (partial index on receive_started_at). */
    public static final String FIND_RECEIVING_STARTED_BEFORE = REF
            + "status = 'RECEIVING' AND receive_started_at <= :cutoff ORDER BY receive_started_at LIMIT :limit";
    /** spec 4.4 step 4: PROCESSING 50 minutes after receipt (partial index on content_received_at). */
    public static final String FIND_PROCESSING_RECEIVED_BEFORE = REF
            + "status = 'PROCESSING' AND content_received_at <= :cutoff ORDER BY content_received_at LIMIT :limit";
    /** spec 4.4 step 4, A-18: a source left after an outcome (partial index on undeleted sources). */
    public static final String FIND_SOURCES_NOT_DELETED_BEFORE = REF
            + "source_storage_key IS NOT NULL AND source_deleted_at IS NULL AND status <> 'PROCESSING'"
            + " AND content_received_at <= :cutoff ORDER BY content_received_at LIMIT :limit";
    /** spec 4.7, A-18: requested content deletions not done (partial index on the open requests). */
    public static final String FIND_DELETIONS_REQUESTED_BEFORE = REF
            + "content_delete_requested_at IS NOT NULL AND content_deleted_at IS NULL"
            + " AND content_delete_requested_at <= :cutoff ORDER BY content_delete_requested_at LIMIT :limit";
    /** spec A-17: UPLOAD_EXPIRED records 24 hours after finishing (partial index on finished_at). */
    public static final String FIND_EXPIRED_FINISHED_BEFORE = REF
            + "status = 'UPLOAD_EXPIRED' AND finished_at <= :cutoff ORDER BY finished_at LIMIT :limit";
    /** spec 4.4 step 3: pending jobs of a kind that are due (partial index on kind and due_at). */
    public static final String FIND_PENDING_JOBS_DUE = "SELECT id, media_id FROM survey_media_jobs"
            + " WHERE state = 'PENDING' AND kind = :kind AND due_at <= :now ORDER BY due_at LIMIT :limit";
    /** spec A-6: a recent MEDIA_LIST row of the user for the checklist (partial index of migration 4). */
    public static final String HAS_RECENT_MEDIA_LIST_ROW = "SELECT EXISTS (SELECT 1 FROM survey_access_logs"
            + " WHERE resource_type = 'MEDIA_LIST' AND user_id = :userId AND resource_id = :checklistId"
            + " AND accessed_at > :since)";

    /** spec A-16 (b): committed photos with the hash on checklists of the lead's other visits. */
    public static final String FIND_COMMITTED_HASH_ON_OTHER_VISITS = "SELECT m.* FROM survey_media m"
            + " JOIN survey_checklists c ON c.id = m.checklist_id"
            + " WHERE m.source_sha256 = :sha256 AND c.lead_id = :leadId AND m.content_received_at IS NOT NULL"
            + " AND c.visit_appointment_id <> (SELECT v.visit_appointment_id FROM survey_checklists v"
            + " WHERE v.id = :checklistId) ORDER BY m.id";
    /** spec 4.4 step 2: the claim of an upload, enforcing the 60-minute window. */
    public static final String CLAIM_IF_AWAITING = "UPDATE survey_media SET status = 'RECEIVING',"
            + " receive_token = :token, receive_key = :attemptKey, receive_started_at = :now, version = version + 1"
            + " WHERE id = :id AND status = 'AWAITING_CONTENT' AND removed_at IS NULL AND created_at > :createdAfter";
    /** spec 4.4 step 2: the release of a claim by its holder. */
    public static final String RELEASE_CLAIM_IF_TOKEN = "UPDATE survey_media SET status = 'AWAITING_CONTENT',"
            + " receive_token = NULL, receive_key = NULL, receive_started_at = NULL, version = version + 1"
            + " WHERE id = :id AND status = 'RECEIVING' AND receive_token = :token";
    /** spec 4.4 step 4: the expiry of a checklist's unclaimed photos. */
    public static final String EXPIRE_STALE = "UPDATE survey_media SET status = 'UPLOAD_EXPIRED',"
            + " status_reason = 'CONTENT_NOT_RECEIVED', finished_at = :now, version = version + 1"
            + " WHERE checklist_id = :checklistId AND status = 'AWAITING_CONTENT' AND created_at <= :cutoff";
    /** spec 4.4 step 1: the set row of a checklist, created once. */
    public static final String INSERT_SET_IGNORING_EXISTING = "INSERT INTO survey_media_sets (checklist_id,"
            + " set_version, last_media_no, updated_at) VALUES (:checklistId, 0, 0, :now) ON CONFLICT DO NOTHING";
    /** spec 4.4 step 2, 4.7: a job of a kind, PENDING from its due time with no attempt yet. */
    public static final String UPSERT_JOB = "INSERT INTO survey_media_jobs (media_id, kind, state, attempts,"
            + " limit_hits, lease_token, due_at, last_error_code, created_at, updated_at)"
            + " VALUES (:mediaId, :kind, 'PENDING', 0, 0, NULL, :dueAt, NULL, :now, :now)"
            + " ON CONFLICT (media_id, kind) DO UPDATE SET state = 'PENDING', attempts = 0, limit_hits = 0,"
            + " lease_token = NULL, due_at = EXCLUDED.due_at, last_error_code = NULL, updated_at = EXCLUDED.updated_at";

    /** spec A-27: how many of the earliest due PROCESS jobs the fair claim chooses from. */
    public static final int CLAIM_WINDOW = 1000;
    /**
     * spec 4.4 step 3, A-27: the next due PROCESS job, fair by the uploader's processing milliseconds of the hour among
     * the earliest due {@link #CLAIM_WINDOW} pending jobs (read through the partial index on kind and due_at, literal
     * predicates, so done jobs are never scanned), skipping busy uploaders; claimed with SKIP LOCKED.
     * {@code j.due_at} repeats the due test on the locked row (SV-093): a claim, renewal, retry or deferral moves due_at
     * while the job stays PENDING, and for a row changed after this statement's snapshot PostgreSQL re-checks only the
     * conditions on {@code j}, not the inner list, so such a row is skipped for the next candidate.
     */
    public static final String CLAIM_NEXT_PROCESS = "UPDATE survey_media_jobs SET lease_token = :token,"
            + " due_at = :leaseUntil, attempts = attempts + 1, updated_at = :now WHERE id = (SELECT j.id"
            + " FROM survey_media_jobs j JOIN survey_media m ON m.id = j.media_id"
            + " LEFT JOIN survey_media_read_budgets b ON b.user_id = m.uploaded_by_user_id"
            + " AND b.hour_start = :hourStart AND b.kind = 'PROC_MS'"
            + " WHERE j.id IN (SELECT p.id FROM survey_media_jobs p WHERE p.state = 'PENDING'"
            + " AND p.kind = 'PROCESS' AND p.due_at <= :now ORDER BY p.due_at LIMIT " + CLAIM_WINDOW + ")"
            + " AND j.state = 'PENDING' AND j.due_at <= :now AND m.uploaded_by_user_id NOT IN (:excluded)"
            + " ORDER BY coalesce(b.read_count, 0), j.due_at, j.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED)"
            + " RETURNING id, media_id, kind, state, attempts, limit_hits, lease_token, due_at, last_error_code,"
            + " created_at, updated_at";
    /** spec 4.7: the earliest due content deletion, through the partial index. */
    public static final String CLAIM_NEXT_DELETE_CONTENT = "UPDATE survey_media_jobs SET lease_token = :token,"
            + " due_at = :leaseUntil, attempts = attempts + 1, updated_at = :now WHERE id = (SELECT j.id"
            + " FROM survey_media_jobs j WHERE j.state = 'PENDING' AND j.kind = 'DELETE_CONTENT' AND j.due_at <= :now"
            + " ORDER BY j.due_at, j.id LIMIT 1 FOR UPDATE SKIP LOCKED)"
            + " RETURNING id, media_id, kind, state, attempts, limit_hits, lease_token, due_at, last_error_code,"
            + " created_at, updated_at";
    /** spec A-17: the oldest UPLOAD_EXPIRED records of a checklist, at most :limit (no job or variant has them). */
    public static final String DELETE_EXPIRED_RECORDS = "DELETE FROM survey_media WHERE id IN (SELECT id FROM"
            + " survey_media WHERE checklist_id = :checklistId AND status = 'UPLOAD_EXPIRED' AND finished_at <= :cutoff"
            + " ORDER BY finished_at, id LIMIT :limit)";
    /** spec 4.4 step 4: quarantine keys still in use. */
    public static final String QUARANTINE_KEYS_IN_USE = "SELECT receive_key FROM survey_media WHERE status ="
            + " 'RECEIVING' AND receive_key IS NOT NULL UNION SELECT source_storage_key FROM survey_media WHERE status ="
            + " 'PROCESSING' AND source_storage_key IS NOT NULL";
    /** spec 4.4 step 4: a PROCESSING photo's job closed by the sweeper, whatever lease it has. */
    public static final String CLOSE_PROCESS_JOB = "UPDATE survey_media_jobs SET state = :state, lease_token = NULL,"
            + " last_error_code = :code, updated_at = :now WHERE media_id = :mediaId AND kind = 'PROCESS'"
            + " AND state = 'PENDING'";
    /** spec A-18: whether work of the kind is due now. */
    public static final String ANY_DUE = "SELECT EXISTS (SELECT 1 FROM survey_media_jobs WHERE state = 'PENDING'"
            + " AND kind = :kind AND due_at <= :now)";
    /** spec A-26: budget and counter rows of hours that ended long ago. */
    public static final String DELETE_OLD_BUDGETS = "DELETE FROM survey_media_read_budgets WHERE hour_start < :cutoff";
    /** spec 4.4 step 4: the stored variant keys among the listed ones. */
    public static final String STORED_VARIANT_KEYS = "SELECT storage_key FROM survey_media_variants"
            + " WHERE storage_key IN (:keys)";
    /** spec A-18: one open incident per kind; the first overdue time stays the earliest. */
    public static final String RECORD_INCIDENT = "INSERT INTO survey_media_incidents (kind, opened_at,"
            + " first_overdue_at, media_count) VALUES (:kind, :now, :first, :count) ON CONFLICT (kind)"
            + " WHERE resolved_at IS NULL DO UPDATE SET media_count = EXCLUDED.media_count, first_overdue_at ="
            + " LEAST(survey_media_incidents.first_overdue_at, EXCLUDED.first_overdue_at)";
    /** spec A-18: a resuming sweep closes the open incidents. */
    public static final String RESOLVE_INCIDENTS = "UPDATE survey_media_incidents SET resolved_at = GREATEST(:now,"
            + " opened_at) WHERE resolved_at IS NULL";
    /** spec A-7: fenced lease renewal. */
    public static final String RENEW_JOB_LEASE = "UPDATE survey_media_jobs SET due_at = :leaseUntil,"
            + " updated_at = :now WHERE id = :id AND lease_token = :token AND state = 'PENDING'";
    /** spec 5.4: the job row lock, after the set and photo rows. */
    public static final String LOCK_JOB = "SELECT id, media_id, kind, state, attempts, limit_hits, lease_token,"
            + " due_at, last_error_code, created_at, updated_at FROM survey_media_jobs WHERE id = :id FOR UPDATE";
    /** Fenced: the job ends. */
    public static final String FINISH_JOB = "UPDATE survey_media_jobs SET state = :state, lease_token = NULL,"
            + " last_error_code = :code, updated_at = :now WHERE id = :id AND lease_token = :token"
            + " AND state = 'PENDING'";
    /** Fenced: the next attempt after its backoff. */
    public static final String RETRY_JOB = "UPDATE survey_media_jobs SET lease_token = NULL, due_at = :dueAt,"
            + " last_error_code = :code, limit_hits = :limitHits, updated_at = :now WHERE id = :id"
            + " AND lease_token = :token AND state = 'PENDING'";
    /** Fenced: waits without using the attempt (spec A-18, scan blocked). */
    public static final String DEFER_JOB = "UPDATE survey_media_jobs SET lease_token = NULL, due_at = :dueAt,"
            + " attempts = attempts - 1, updated_at = :now WHERE id = :id AND lease_token = :token"
            + " AND state = 'PENDING'";
    public static final String UPLOADER_OF_MEDIA = "SELECT uploaded_by_user_id FROM survey_media WHERE id = :id";

    /** spec A-3: the per-user advisory transaction lock. */
    public static final String LOCK_USER = "SELECT pg_advisory_xact_lock(:namespace, hashtext(:userId))";
    /** spec A-3: records and declared bytes of the user after a time (index on uploader and created_at). */
    public static final String HOURLY_USAGE = "SELECT count(*) AS records, coalesce(sum(declared_size_bytes), 0)"
            + " AS bytes, min(created_at) AS oldest FROM survey_media WHERE uploaded_by_user_id = :userId"
            + " AND created_at > :since";
    public static final String COUNTER_VALUE = "SELECT read_count FROM survey_media_read_budgets"
            + " WHERE user_id = :userId AND hour_start = :hourStart AND kind = :kind";
    /** spec A-26: one more read while below the effective limit; no row returned refuses. */
    public static final String COUNT_IF_BELOW = "INSERT INTO survey_media_read_budgets AS b (user_id, hour_start,"
            + " kind, read_count) VALUES (:userId, :hourStart, :kind, 1) ON CONFLICT (user_id, hour_start, kind)"
            + " DO UPDATE SET read_count = b.read_count + 1 WHERE b.read_count < :effectiveLimit"
            + " RETURNING b.read_count";
    /** spec A-26: the first refusal of the hour marks it; the returned mark tells whether this was the first. */
    public static final String INSERT_EXCEEDED = "INSERT INTO survey_media_read_budgets (user_id, hour_start, kind,"
            + " read_count, exceeded_at) VALUES (:userId, :hourStart, :kind, 0, :now)"
            + " ON CONFLICT (user_id, hour_start, kind) DO NOTHING";
    /** spec A-26: sets the mark only when none is set; one row updated means this call set it. */
    public static final String MARK_EXCEEDED_IF_NONE = "UPDATE survey_media_read_budgets SET exceeded_at = :now"
            + " WHERE user_id = :userId AND hour_start = :hourStart AND kind = :kind AND exceeded_at IS NULL";
    /** spec A-27: the worker's unconditional increments of PROC_MS and EXPENSIVE. */
    public static final String ADD_TO_COUNTER = "INSERT INTO survey_media_read_budgets AS b (user_id, hour_start,"
            + " kind, read_count) VALUES (:userId, :hourStart, :kind, :amount)"
            + " ON CONFLICT (user_id, hour_start, kind) DO UPDATE SET read_count = b.read_count + EXCLUDED.read_count";
    /** spec A-19: the lock of one refusal-event key. */
    public static final String TRY_LOCK_EVENT_KEY = "SELECT pg_try_advisory_xact_lock(:namespace, hashtext(:key))";
    /** spec A-19: an event of the key in the last minute, on the (user_id, occurred_at) index. */
    public static final String HAS_RECENT_EVENT = "SELECT EXISTS (SELECT 1 FROM survey_security_events"
            + " WHERE user_id = :userId AND occurred_at > :since AND resource_id = :resourceId AND action = :action"
            + " AND outcome = :outcome)";

    private MediaSql() {
    }
}
