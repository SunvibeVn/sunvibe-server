package vn.sunvibe.brokerage.core.application.shared.external.property;

/**
 * Where an agreement's unit is (E05-S5 spec S5-D2): its code, its location label, its property's street and ward name;
 * each null when property-service holds none. The owner's own property, shown to the owner only (brokerage-agreement
 * BR-17): never logged.
 */
public record UnitLocationFacts(String unitId, String unitCodeOrNull, String locationLabelOrNull, String streetOrNull,
                                String wardNameOrNull) {

    @Override
    public String toString() {
        return "UnitLocationFacts[" + unitId + "]";
    }
}
