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
     * The version was not answered within 7 days (BR-10; E05-S5 spec S5-D8): no actor, the time is its deadline, written
     * once by whichever stores the lapse.
     */
    VERSION_LAPSED,
    /** The assigned Sale opened the next draft version after a rejection or a lapse (US-2 AC-3, E-6; spec S5-D10). */
    DRAFT_OPENED
}
