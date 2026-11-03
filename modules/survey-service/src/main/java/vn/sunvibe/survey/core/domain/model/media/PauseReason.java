package vn.sunvibe.survey.core.domain.model.media;

/** Why intake is paused (survey-media spec 5.2, A-18). */
public enum PauseReason {
    /** No complete sweep yet since the health row was seeded. */
    NOT_STARTED,
    /** The store probe failed. */
    STORE,
    /** The scanner probe failed. */
    SCANNER,
    /** The processor probe failed. */
    PROCESSOR,
    /** The last complete sweep is older than 10 minutes. */
    SWEEP_STALE,
    /** The processing worker reported no heartbeat for 10 minutes while work is due. */
    WORKER_STALE,
    /** The deletion executor reported no heartbeat for 10 minutes while work is due. */
    DELETION_STALE,
    /** Processing is switched off (survey-media spec A-1). */
    PROCESSING_OFF,
    /** A raw source, removed content or an orphan object is overdue for deletion. */
    DELETION_OVERDUE
}
