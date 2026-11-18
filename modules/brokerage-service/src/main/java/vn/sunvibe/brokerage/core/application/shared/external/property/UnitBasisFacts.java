package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.List;

/**
 * The basis entries of one unit, read at one moment (E05-S6 spec S6-D9, S6-D17; read 1 of ADR-012 decision 2).
 *
 * @param unitId     the unit
 * @param propertyId its property
 * @param entries    one per lead whose latest COMPLETED intake maps the unit; none for a unit no such intake maps
 */
public record UnitBasisFacts(String unitId, String propertyId, List<Entry> entries) {

    public UnitBasisFacts {
        entries = List.copyOf(entries);
    }

    /**
     * The latest COMPLETED intake of one lead that maps the unit.
     *
     * @param intakeId    the intake row
     * @param state       the readiness state of that lead (USABLE, SUSPENDED, ENDED), as property-service names it
     * @param unitReasons the reasons of this unit in that readiness, as property-service names them
     */
    public record Entry(String intakeId, String state, List<String> unitReasons) {

        public Entry {
            unitReasons = List.copyOf(unitReasons);
        }
    }
}
