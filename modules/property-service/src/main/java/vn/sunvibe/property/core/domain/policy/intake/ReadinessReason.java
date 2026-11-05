package vn.sunvibe.property.core.domain.policy.intake;

/** Why a result is not usable (property-create intake spec 4.11). The first five suspend it, the others end it. */
public enum ReadinessReason {
    LEAD_ON_HOLD,
    EVIDENCE_HELD,
    OWNER_NOT_CONFIRMED,
    OWNER_NOT_ELIGIBLE,
    RESTRICTED,
    DUPLICATE,
    REJECTED,
    WITHDRAWN,
    REQUALIFIED,
    INTENT_CHANGED,
    EVIDENCE_GONE
}
