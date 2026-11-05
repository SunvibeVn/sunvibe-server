package vn.sunvibe.property.core.application.shared.external.lead;

import java.time.Instant;

/**
 * One History entry of an intake (property-create intake spec 4.12): reference only, so it can carry no property, owner,
 * code or address.
 *
 * @param actorUserIdOrNull null for an owner's withdrawal, so no owner name appears
 * @param intakeIdOrNull    null with the number, for a suspension recorded before any intake
 */
public record IntakeHistoryEntry(Type type, String actorUserIdOrNull, String intakeIdOrNull, Integer intakeNoOrNull,
                                 Instant occurredAt) {

    /** The History types of spec 4.12. */
    public enum Type {
        INVENTORY_INTAKE_COMPLETED,
        INVENTORY_INTAKE_SUSPENDED,
        INVENTORY_INTAKE_RESTORED
    }
}
