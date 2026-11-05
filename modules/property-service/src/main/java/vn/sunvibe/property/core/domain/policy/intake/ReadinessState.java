package vn.sunvibe.property.core.domain.policy.intake;

/** Whether a completed result can be used (property-create intake spec 4.11). */
public enum ReadinessState {
    /** The lead has no COMPLETED intake. */
    NONE,
    USABLE,
    SUSPENDED,
    ENDED
}
