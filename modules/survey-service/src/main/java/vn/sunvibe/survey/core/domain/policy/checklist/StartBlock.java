package vn.sunvibe.survey.core.domain.policy.checklist;

/** Why a checklist cannot be started now, first that applies (survey-checklist spec 7.2). */
public enum StartBlock {
    /** No VISITED appointment in the lead's current intent cycle. */
    NO_CURRENT_VISIT,
    /** The current visit is not for long-term rental. */
    INTENT,
    /** The lead is not in SURVEY_SCHEDULED. */
    LEAD_STATUS,
    /** The current visit already has a draft. */
    ALREADY_STARTED,
    /** The viewer is not the lead's assignee. */
    NOT_ASSIGNEE,
    /** The assignee can no longer take leads. */
    ASSIGNEE_INELIGIBLE
}
