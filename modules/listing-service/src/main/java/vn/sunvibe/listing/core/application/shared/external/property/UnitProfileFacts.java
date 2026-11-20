package vn.sunvibe.listing.core.application.shared.external.property;

/**
 * A unit's profile as property publishes it, for a revision's unit snapshot (listing-publish E06-S2 spec E06S2-D12):
 * the property's asset type code, the catalog province and ward with their codes and names (all four or none), and the
 * unit's rooms and area. {@code java.*} values only. Unpublished: no text here names a value.
 */
public record UnitProfileFacts(String unitId, String assetTypeOrNull, String provinceCodeOrNull,
                               String provinceNameOrNull, String wardCodeOrNull, String wardNameOrNull, int bedrooms,
                               int bathrooms, double areaSqm) {

    @Override
    public String toString() {
        return "UnitProfileFacts[hidden]";
    }
}
