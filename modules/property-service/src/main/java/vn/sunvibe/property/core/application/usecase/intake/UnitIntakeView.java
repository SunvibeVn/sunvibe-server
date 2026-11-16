package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/**
 * One unit as {@link UnitIntakeUseCase} publishes it (E05-S6b S6b-D1, S6b-D2). No lead id, no lead-level reason, no owner.
 *
 * @param unitId            the unit
 * @param unitCodeOrNull    its code as stored
 * @param propertyId        its property
 * @param availabilityState its availability as stored ({@code units.availability_state}); an unknown value is passed as it
 *                          is
 * @param intakes           one entry per lead whose latest COMPLETED intake maps the unit, ordered by intake id; none for a
 *                          unit no such intake maps
 */
public record UnitIntakeView(String unitId, String unitCodeOrNull, String propertyId, String availabilityState,
                             List<IntakeEntry> intakes) {

    public UnitIntakeView {
        intakes = List.copyOf(intakes);
    }

    /**
     * The latest COMPLETED intake of one lead that maps the unit.
     *
     * @param intakeId         the intake row
     * @param state            the lead-level state of the lead's readiness: USABLE, SUSPENDED or ENDED
     * @param sourceRevisionId the survey revision the intake stands on
     * @param unitReasons      the reasons of this unit in that readiness (RESTRICTED, AVAILABILITY_DIFFERS, PROPERTY_STOP,
     *                         UNIT_STOP), in its order
     */
    public record IntakeEntry(String intakeId, String state, String sourceRevisionId, List<String> unitReasons) {

        public IntakeEntry {
            unitReasons = List.copyOf(unitReasons);
        }
    }
}
