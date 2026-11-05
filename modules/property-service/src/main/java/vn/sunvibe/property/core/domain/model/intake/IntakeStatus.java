package vn.sunvibe.property.core.domain.model.intake;

/** The statuses of an inventory intake (property-create intake spec 4.2). Stored as text in {@code inventory_intakes.status}. */
public enum IntakeStatus {
    /** A selection exists and nothing is pending on the owner. */
    DRAFT,
    /** A confirmation request went out. */
    AWAITING_OWNER,
    /** The account confirmed; the manager's check, target, availability and completion follow. */
    OWNER_CONFIRMED,
    /** A manager must rule on a possible duplicate. */
    CASE_OPEN,
    /** Inventory stands. */
    COMPLETED,
    /** The row's source died; a new intake starts from a new row. */
    ABANDONED;

    public boolean isOpen() {
        return this != COMPLETED && this != ABANDONED;
    }
}
