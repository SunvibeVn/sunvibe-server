package vn.sunvibe.survey.core.domain.policy.checklist;

/** Where a space's effective charge comes from (survey-checklist spec 7.2). */
public enum ChargeOrigin {
    /** The common charge applies to the space. */
    COMMON,
    /** The space has its own charge. */
    SPACE
}
