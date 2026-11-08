package vn.sunvibe.survey.core.domain.policy.checklist;

/** Why the viewer cannot decide a revision now, in the order of the command (survey-checklist submission spec 4.2, 5.5). */
public enum ReviewReason {
    /** Not a SALE_LEADER, SALES_MANAGER or ADMIN, or the account cannot sign in (plan PD-6). */
    NOT_REVIEWER_ROLE,
    /** The revision is not waiting for a decision. */
    NOT_SUBMITTED,
    /** The lead is not SURVEY_SCHEDULED. */
    LEAD_STATUS,
    /** The revision's cycle ended, or it is no longer on the cycle's current visit. */
    INTENT_CHANGED,
    /** Photos cannot be served, so nobody can see the evidence (survey-checklist submission spec A-3). */
    SERVING_DISABLED,
    /** The viewer touched the record or is the lead's assignee (survey-checklist submission spec 4.2). */
    NOT_INDEPENDENT
}
