package vn.sunvibe.listing.core.application.shared.external.property;

/**
 * Where a unit is (listing-publish E06-S4 spec E06S4-D12): its code, its location label inside the property, the
 * property's street and ward name, as property's location read answers them. Private: its text names the unit id only,
 * and no value reaches a log.
 */
public record UnitAddressFacts(String unitId, String unitCodeOrNull, String locationLabelOrNull, String streetOrNull,
                               String wardNameOrNull) {

    @Override
    public String toString() {
        return "UnitAddressFacts[" + unitId + "]";
    }
}
