package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.List;

/**
 * The intake basis of a lead: its latest COMPLETED intake, read at one moment.
 *
 * @param intakeId              the intake row
 * @param propertyId            its result property
 * @param state                 its readiness state (USABLE, SUSPENDED, ENDED), as property-service names it
 * @param units                 the units it maps, in the readiness order
 * @param declaredOwnerIdOrNull the declared owner of the property; null when it has none
 * @param holdings              every ownership row of the property valid today, any relation and verification
 */
public record IntakeBasisFacts(String intakeId, String propertyId, String state, List<Unit> units,
                               String declaredOwnerIdOrNull, List<Holding> holdings) {

    public IntakeBasisFacts {
        units = List.copyOf(units);
        holdings = List.copyOf(holdings);
    }

    /**
     * One unit the basis maps.
     *
     * @param unitCodeOrNull its code; null when the unit row is missing
     * @param reasons        the reasons of its readiness, as property-service names them
     */
    public record Unit(String unitId, String unitCodeOrNull, List<String> reasons) {

        public Unit {
            reasons = List.copyOf(reasons);
        }
    }

    /** One ownership row valid today, its values as stored. */
    public record Holding(String userId, String relation, String verification) {
    }
}
