package vn.sunvibe.auth.core.application.usecase.role;

/**
 * The notices an account gets about an owner link (property-create intake D5-7, A-12), a brokerage agreement waiting
 * for its answer (brokerage-agreement E05-S4 spec S4-D7) or one put in effect (E05-S6 spec S6-D13): fixed text, no code
 * or address.
 */
public enum OwnerNoticeKind {
    /** The account confirmed an owner link. */
    OWNER_CONFIRMED,
    /** The account withdrew an owner link. */
    OWNER_WITHDRAWN,
    /** A manager could not confirm the link by phone. Neutral on purpose: it is never a withdrawal. */
    OWNER_CHECK_REFUSED,
    /**
     * A brokerage agreement version waits for the account's answer (brokerage-agreement BR-17). Fixed text that only
     * invites a sign-in: no term, unit, address, amount, agreement id or link.
     */
    AGREEMENT_PENDING,
    /**
     * A brokerage agreement the account confirmed was signed and activated by Sunvibe (brokerage-agreement US-3 AC-5,
     * BR-17; E05-S6 spec S6-D13). Fixed text that only invites a sign-in: no term, unit, address, amount, contract
     * number or link.
     */
    AGREEMENT_ACTIVATED
}
