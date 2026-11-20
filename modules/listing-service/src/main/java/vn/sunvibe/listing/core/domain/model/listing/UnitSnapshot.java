package vn.sunvibe.listing.core.domain.model.listing;

import java.util.Set;

/**
 * The unit and property fields a revision keeps from its submission (listing-publish US-2 AC-5, BR-4; E06-S2 spec
 * E06S2-D12): the property's asset type (property's D-47 codes, null on a legacy row), the ward and its province from
 * the catalog only (all four set, or none when the property's ward is not in the catalog), and the unit's bedrooms,
 * bathrooms and area. A later change of the unit does not change it. Unpublished: no text of it names a value.
 */
public record UnitSnapshot(String assetTypeOrNull, String provinceCodeOrNull, String provinceNameOrNull,
                           String wardCodeOrNull, String wardNameOrNull, int bedrooms, int bathrooms, double areaSqm) {

    /** property-service's asset types (D-47). */
    public static final Set<String> ASSET_TYPES =
            Set.of("HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT");

    public UnitSnapshot {
        if (assetTypeOrNull != null && !ASSET_TYPES.contains(assetTypeOrNull)) {
            throw new IllegalArgumentException("the asset type is not one of property's codes");
        }
        boolean anyPlace = provinceCodeOrNull != null || provinceNameOrNull != null || wardCodeOrNull != null
                || wardNameOrNull != null;
        boolean wholePlace = provinceCodeOrNull != null && provinceNameOrNull != null && wardCodeOrNull != null
                && wardNameOrNull != null;
        if (anyPlace && !wholePlace) {
            throw new IllegalArgumentException("the ward and province are taken together from the catalog");
        }
        if (bedrooms < 0 || bathrooms < 0 || !Double.isFinite(areaSqm) || areaSqm < 0) {
            throw new IllegalArgumentException("rooms and area are counted from 0");
        }
    }

    @Override
    public String toString() {
        return "UnitSnapshot[hidden]";
    }
}
