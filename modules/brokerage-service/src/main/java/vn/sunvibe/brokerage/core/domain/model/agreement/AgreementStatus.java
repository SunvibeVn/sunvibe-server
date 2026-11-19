package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The states of an agreement (brokerage-agreement BR-5). The whole vocabulary is named once: the database CHECK and the
 * API's allowed values hold every code in this declaration order. Every time-driven exit from the unfinished set (the
 * expiry at the end of the period) is a stored transition, written when it is due by the slice that builds it, and a
 * command that needs the unit's slot applies a due one first (E05-S2 spec S2-D4): the expiry is stored by
 * AgreementExpiryJob, and the create stores a due one before it inserts (E05-S8 spec S8-D2, S8-D4).
 */
public enum AgreementStatus {
    DRAFTING,
    AWAITING_CONFIRMATION,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    TERMINATED,
    CANCELLED;

    /**
     * brokerage-agreement BR-1: at most one unfinished agreement (drafting, awaiting confirmation, active, suspended)
     * per unit and transaction type.
     */
    public boolean isUnfinished() {
        return this == DRAFTING || this == AWAITING_CONFIRMATION || this == ACTIVE || this == SUSPENDED;
    }
}
