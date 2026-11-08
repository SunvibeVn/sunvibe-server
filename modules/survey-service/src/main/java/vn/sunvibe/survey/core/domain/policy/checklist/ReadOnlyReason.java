package vn.sunvibe.survey.core.domain.policy.checklist;

/** Why a viewer cannot edit a revision, first that applies (survey-checklist spec 4.4, survey-checklist submission spec 5.1). */
public enum ReadOnlyReason {
    /** The requirement changed after this visit, also when it changed back. */
    INTENT_CHANGED,
    /** The revision was submitted and waits for its decision. */
    SUBMITTED,
    /** The revision was decided. */
    DECIDED,
    /** A newer VISITED appointment exists in the checklist's cycle. */
    NEWER_VISIT,
    /** The lead is not in SURVEY_SCHEDULED. */
    LEAD_STATUS,
    /** The viewer is not the lead's assignee. */
    NOT_ASSIGNEE,
    /** The assignee can no longer take leads. */
    ASSIGNEE_INELIGIBLE,
    /** The assignee is not the holder and must take the draft over first. */
    TAKEOVER_REQUIRED
}
