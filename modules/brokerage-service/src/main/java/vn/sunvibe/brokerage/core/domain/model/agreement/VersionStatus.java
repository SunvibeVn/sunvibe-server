package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The states of an agreement version (brokerage-agreement BR-5). The whole vocabulary is named once: the database CHECK
 * and the API's allowed values hold every code in this declaration order. Every time-driven exit from the pending set
 * (a lapse after 7 or 14 days, the start of an activated version, the cancellation of the pending version when the
 * agreement ends) is a stored transition, written when it is due by the slice that builds it, and a command that needs
 * the version slot applies a due one first (E05-S2 spec S2-D4). The 7-day lapse is stored by AgreementLapseJob
 * (E05-S5); the 14-day lapse, the start and the cancellation are not reachable yet.
 */
public enum VersionStatus {
    DRAFT,
    AWAITING_OWNER,
    OWNER_CONFIRMED,
    ACTIVATED,
    EFFECTIVE,
    SUPERSEDED,
    OWNER_REJECTED,
    LAPSED,
    ACTIVATION_REFUSED,
    CANCELLED;

    /** brokerage-agreement BR-5: one pending version at a time, pending being from the draft to the activated one. */
    public boolean isPending() {
        return this == DRAFT || this == AWAITING_OWNER || this == OWNER_CONFIRMED || this == ACTIVATED;
    }
}
