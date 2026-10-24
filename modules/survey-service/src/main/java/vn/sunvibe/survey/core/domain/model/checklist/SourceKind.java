package vn.sunvibe.survey.core.domain.model.checklist;

/** How a sourced value came to the checklist (survey-checklist BR-17, BR-19, BR-21). */
public enum SourceKind {
    /** Observed, measured or estimated on site at the draft's visit. */
    ON_SITE,
    /** Said by the owner to a staff member who really heard it. */
    OWNER_STATED,
    /** Held by the owner's own web submission. */
    OWNER_FORM
}
