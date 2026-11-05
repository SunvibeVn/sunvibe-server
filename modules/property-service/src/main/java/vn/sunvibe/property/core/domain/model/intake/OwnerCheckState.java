package vn.sunvibe.property.core.domain.model.intake;

/** The manager's check of a proof (property-create intake D5-7). Stored as text in {@code owner_check}. */
public enum OwnerCheckState {
    /** Every proof starts here. */
    REQUIRED,
    /** The bound account entered the phrase in time. */
    DONE,
    /** A manager reached a stranger: the proof is void. */
    REFUSED
}
