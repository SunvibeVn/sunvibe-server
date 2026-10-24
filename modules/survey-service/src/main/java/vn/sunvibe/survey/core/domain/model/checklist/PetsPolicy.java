package vn.sunvibe.survey.core.domain.model.checklist;

/** Whether pets are allowed (survey-checklist BR-5). */
public enum PetsPolicy {
    /** Allowed. */
    ALLOWED,
    /** Not allowed. */
    NOT_ALLOWED,
    /** Allowed on conditions in the note. */
    CONDITIONAL
}
