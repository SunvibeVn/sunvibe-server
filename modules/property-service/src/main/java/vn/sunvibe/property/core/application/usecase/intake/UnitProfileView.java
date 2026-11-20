package vn.sunvibe.property.core.application.usecase.intake;

/**
 * A unit's profile as a listing snapshots it (listing-publish E06-S2 spec E06S2-D12): the property's asset type (one of
 * the D-47 codes, null on a legacy row), the catalog province and ward with their codes and names (all four or none),
 * and the unit's bedrooms, bathrooms and area. Unpublished values: never logged.
 */
public record UnitProfileView(String unitId, String assetTypeOrNull, String provinceCodeOrNull,
                              String provinceNameOrNull, String wardCodeOrNull, String wardNameOrNull, int bedrooms,
                              int bathrooms, double areaSqm) {

    /** listing-publish US-10 AC-4: no value of an unpublished listing in a log line. */
    @Override
    public String toString() {
        return "UnitProfileView[hidden]";
    }
}
