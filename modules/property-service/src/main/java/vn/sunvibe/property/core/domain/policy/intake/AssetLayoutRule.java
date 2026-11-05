package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;

/** Which layout each asset type takes (property-create intake spec 4.8, US-4 AC-1, D5-13). */
public final class AssetLayoutRule {

    private AssetLayoutRule() {
    }

    public static boolean allows(AssetType assetType, RentalLayout layout) {
        if (assetType == null || layout == null) {
            return false;
        }
        return switch (assetType) {
            case HOUSE, VILLA, APARTMENT, CONDOTEL -> layout == RentalLayout.WHOLE;
            case WHOLE_BUILDING -> layout == RentalLayout.PARTS;
            case HOMESTAY -> true;
        };
    }
}
