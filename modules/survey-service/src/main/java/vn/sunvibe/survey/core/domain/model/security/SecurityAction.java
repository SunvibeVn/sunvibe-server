package vn.sunvibe.survey.core.domain.model.security;

/** What the refused caller tried (survey-checklist spec 4.10). */
public enum SecurityAction {
    /** A read. */
    READ,
    /** A checklist start. */
    START,
    /** A group save. */
    SAVE,
    /** A takeover. */
    TAKEOVER
}
