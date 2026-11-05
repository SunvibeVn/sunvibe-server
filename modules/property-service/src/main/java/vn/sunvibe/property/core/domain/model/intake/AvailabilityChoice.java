package vn.sunvibe.property.core.domain.model.intake;

import vn.sunvibe.property.core.domain.model.unit.UnitAvailabilityState;

/**
 * What the owner said about a space at the intake (property-create intake spec 4.9, property-create BR-11). No choice stands for "not
 * contacted": without a contact there is no completion (E-14).
 */
public enum AvailabilityChoice {
    VACANT(UnitAvailabilityState.AVAILABLE),
    OCCUPIED(UnitAvailabilityState.OCCUPIED),
    UNDER_REPAIR(UnitAvailabilityState.MAINTENANCE),
    PAUSED(UnitAvailabilityState.BLOCKED),
    OWNER_UNSURE(UnitAvailabilityState.BLOCKED),
    SOURCE_CONFLICT(UnitAvailabilityState.BLOCKED);

    private final UnitAvailabilityState unitState;

    AvailabilityChoice(UnitAvailabilityState unitState) {
        this.unitState = unitState;
    }

    public UnitAvailabilityState unitState() {
        return unitState;
    }
}
