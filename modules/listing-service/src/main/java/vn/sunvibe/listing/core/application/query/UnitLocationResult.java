package vn.sunvibe.listing.core.application.query;

/**
 * The real location of a revision's unit as an eligible reviewer opens it (listing-publish US-4 AC-2; E06-S4 spec
 * section 4.5, E06S4-D12): the unit's code, its location label, the property's street and ward name. Private: never
 * logged, and its text names no value.
 */
public record UnitLocationResult(String unitCodeOrNull, String locationLabelOrNull, String streetOrNull,
                                 String wardNameOrNull) {

    @Override
    public String toString() {
        return "UnitLocationResult[hidden]";
    }
}
