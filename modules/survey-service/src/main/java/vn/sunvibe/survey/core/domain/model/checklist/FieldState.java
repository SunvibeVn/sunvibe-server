package vn.sunvibe.survey.core.domain.model.checklist;

/** The three states of a checklist field (survey-checklist BR-16, spec 4.6). */
public enum FieldState {
    /** Not checked or not confirmed yet; never stored as 0 or as not applicable. */
    UNKNOWN,
    /** A value was recorded. */
    VALUE,
    /** The part really does not exist or is not in the configuration, with a reason. */
    NOT_APPLICABLE
}
