package vn.sunvibe.survey.core.domain.model.checklist;

/** The state of a hazard at the draft's visit (survey-checklist BR-18, BR-24). */
public enum HazardStatus {
    /** Seen and not fixed. */
    OPEN,
    /** The owner says it was fixed; the owner's word keeps the warning. */
    OWNER_REPORTED_FIXED,
    /** Seen resolved at this checklist's visit. */
    OBSERVED_RESOLVED
}
