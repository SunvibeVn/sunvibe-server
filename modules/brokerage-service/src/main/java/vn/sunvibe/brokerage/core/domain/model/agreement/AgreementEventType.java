package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The events of an agreement's history (brokerage-agreement BR-17: the history holds the event, the actor, the time
 * and the version only). Later slices add their events.
 */
public enum AgreementEventType {
    CREATED,
    /**
     * One save of a draft's content (E05-S3 spec S3-D10): one row per save with the version, the actor and the time. The
     * authors of a version are its creator and every actor of these rows.
     */
    DRAFT_EDITED,
    /**
     * The draft sent to the owner (brokerage-agreement US-1 AC-5; E05-S4 spec S4-D6, S4-D13): one row with the version,
     * the actor and the time. The sender is recorded here only.
     */
    VERSION_SENT,
    /**
     * The owner confirmed the version (brokerage-agreement US-2 AC-2, AC-4; E05-S5 spec S5-D12): one row per version,
     * the owner as actor. The statement version stays on the version row.
     */
    OWNER_CONFIRMED,
    /** The owner declined the version (US-2 AC-3): the owner as actor; the reason stays on the version row (BR-17). */
    OWNER_REJECTED,
    /**
     * The version lapsed: not answered by the owner within 7 days (BR-10; E05-S5 spec S5-D8), or confirmed and not
     * activated within 14 days (BR-11; E05-S6 spec S6-D5); the version row tells which. No actor, the time is the
     * deadline of the lapse that fell due, written once by whichever stores the lapse.
     */
    VERSION_LAPSED,
    /** The assigned Sale opened the next draft version after a rejection or a lapse (US-2 AC-3, E-6; spec S5-D10). */
    DRAFT_OPENED,
    /**
     * An independent manager activated the version (US-3 AC-2; E05-S6 spec S6-D15): the manager as actor. The document's
     * values and the statement version stay on the version row (BR-17).
     */
    ACTIVATED,
    /**
     * A manager refused to activate the version (US-3 AC-3, E-7; E05-S6 spec S6-D15): the manager as actor; the refusal
     * type and the note stay on the version row (BR-17).
     */
    ACTIVATION_REFUSED,
    /**
     * The activated version took effect at its start (BR-5, BR-6, US-3 AC-5; E05-S6 spec S6-D6): no actor, the time is
     * the start of its start date in Vietnam, written once by the start store.
     */
    VERSION_STARTED,
    /**
     * A manager suspended the agreement in effect (BR-15, US-5 AC-2; E05-S8 spec S8-D10): the manager as actor, the
     * agreement's latest version. The reason and the note stay on the suspension record (BR-17).
     */
    SUSPENDED,
    /**
     * A manager who is not the basis lead's assignee lifted the suspension (BR-15, US-5 AC-5; E05-S8 spec S8-D10): the
     * manager as actor, the agreement's latest version. The reason stays on the suspension record (BR-17).
     */
    SUSPENSION_RELEASED,
    /**
     * Staff terminated the agreement (BR-14, US-5 AC-2; E05-S8 spec S8-D10): the staff member as actor, the agreement's
     * latest version. The type, the date, the channel and the note stay on the agreement row (BR-17).
     */
    TERMINATED,
    /**
     * The agreement expired at the end of its version's end date (BR-6, US-5 AC-1; E05-S8 spec S8-D2, S8-D10): no actor,
     * the time is the end of the version that counts, which is the row's version; written once by whichever stores it.
     */
    EXPIRED,
    /**
     * The assigned Sale cancelled an agreement that never took effect (BR-5, US-1 AC-5; E05-S8 spec S8-D10): the Sale as
     * actor, the agreement's latest version.
     */
    CANCELLED
}
