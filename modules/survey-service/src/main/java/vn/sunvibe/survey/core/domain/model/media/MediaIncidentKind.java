package vn.sunvibe.survey.core.domain.model.media;

/** An overdue deletion, recorded as a minimal incident (survey-media spec A-18, BR-18). */
public enum MediaIncidentKind {
    /** A raw source, or a quarantine object, older than 60 minutes. */
    SOURCE_OVERDUE,
    /** Removed content not deleted 24 hours after its request (held photos have no request). */
    CONTENT_DELETION_OVERDUE,
    /** An object under {@code media/} that no committed row references, older than 24 hours. */
    ORPHAN_OVERDUE
}
