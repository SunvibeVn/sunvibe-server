package vn.sunvibe.acquisition.core.domain.model.activity;

/**
 * Kinds of entries in the history of an owner lead.
 */
public enum OwnerLeadActivityType {
    /** The owner sent the intake form. */
    SUBMITTED,
    /** The system gave the lead to the staff member responsible for the ward. */
    AUTO_ASSIGNED,
    /** A manager gave the lead to a staff member. */
    ASSIGNED,
    /** A manager moved the lead from one staff member to another. */
    REASSIGNED,
    /** The lead moved to another status. */
    STATUS_CHANGED,
    /** Staff added a note without changing the lead. */
    NOTE_ADDED
}
