package vn.sunvibe.property.core.domain.policy.intake;

/** Why the intake gate is closed (property-create intake spec 4.4). */
public enum GateReason {
    NOT_LONG_TERM,
    /** ON_HOLD, closed and every other status but QUALIFIED. */
    NOT_QUALIFIED,
    NO_PASSED,
    PASSED_NOT_USABLE,
    TYPE_SCOPE_MISMATCH,
    RECONCILIATION_REQUIRED,
    /** An unreleased whole-lead suspension. */
    RESTRICTED
}
