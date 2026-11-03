package vn.sunvibe.survey.core.domain.model.media;

/** The per-user hourly counters of survey-media (spec 5.2, A-26, A-27). */
public enum MediaCounterKind {
    /** FULL reads (inline or download), budget 600 an hour. */
    FULL,
    /** PREVIEW reads, budget 1,200 an hour. */
    PREVIEW,
    /** THUMBNAIL reads, budget 6,000 an hour. */
    THUMBNAIL,
    /** Media list reads (204 included), budget 1,200 an hour. */
    LIST,
    /** Expensive processing outcomes of the uploader; create and content refuse at 20 (spec A-27). */
    EXPENSIVE,
    /** Processing milliseconds of the uploader, ordering the worker's fair claim (spec A-27). */
    PROC_MS
}
