package vn.sunvibe.survey.core.domain.policy.checklist;

/** Why a new revision cannot be started on the current visit now (survey-checklist submission spec 4.2, 7.2). */
public enum RevisionStartBlock {
    /** The cycle has no current visit. */
    NO_CURRENT_VISIT,
    /** The lead is not SURVEY_SCHEDULED. */
    LEAD_STATUS,
    /** The visit has no revision yet, or its latest one is not NEEDS_CHANGES DOCUMENTS_ONLY. */
    NOT_RETURNED_FOR_DOCUMENTS,
    /** The viewer is not the lead's assignee. */
    NOT_ASSIGNEE,
    /** The assignee can no longer take leads. */
    ASSIGNEE_INELIGIBLE
}
