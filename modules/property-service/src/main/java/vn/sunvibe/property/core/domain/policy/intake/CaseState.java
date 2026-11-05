package vn.sunvibe.property.core.domain.policy.intake;

/** The cases of an intake as its block shows them (property-create intake spec 4.10). */
public enum CaseState {
    NONE,
    /** A case is undecided. */
    OPEN,
    /** A SAME_ASSET decision holds this intake. */
    WAITING,
    /** Every case is decided and this intake is released. */
    DECIDED
}
