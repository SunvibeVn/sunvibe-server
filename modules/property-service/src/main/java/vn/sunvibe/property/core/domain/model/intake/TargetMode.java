package vn.sunvibe.property.core.domain.model.intake;

/** What completion does with the selection (property-create intake spec 4.7, 4.8). */
public enum TargetMode {
    /** A new property and units. */
    CREATE,
    /** The selected spaces map onto existing units of the owner's property. */
    LINK
}
