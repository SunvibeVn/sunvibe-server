package vn.sunvibe.acquisition.core.application.usecase.inventory;

/** The History types of the inventory intake (property-create intake spec 4.12). Reference only. */
public enum LeadIntakeEvent {
    /** The intake completed. */
    INVENTORY_INTAKE_COMPLETED,
    /** A manager suspended use, or the owner withdrew the proof in force of a completed result. */
    INVENTORY_INTAKE_SUSPENDED,
    /** A suspension was released, or the result was restored on a new proof. */
    INVENTORY_INTAKE_RESTORED
}
