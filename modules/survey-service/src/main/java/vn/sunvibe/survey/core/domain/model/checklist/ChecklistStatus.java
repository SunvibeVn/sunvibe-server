package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * The status of a checklist revision (survey-checklist submission spec 4.1): DRAFT, then SUBMITTED, then one decided
 * status; nothing returns to DRAFT.
 */
public enum ChecklistStatus {
    /** Being recorded. */
    DRAFT,
    /** Frozen and waiting for a reviewer (survey-checklist US-2 AC-3). */
    SUBMITTED,
    /** Decided: accepted (survey-checklist US-3 AC-1). */
    PASSED,
    /** Decided: returned for more documents or for a re-survey (survey-checklist US-3 AC-2). */
    NEEDS_CHANGES,
    /** Decided: not accepted, or temporarily not possible (survey-checklist D3-15). */
    FAILED;

    /** A reviewer has decided this revision. */
    public boolean isDecided() {
        return this == PASSED || this == NEEDS_CHANGES || this == FAILED;
    }
}
