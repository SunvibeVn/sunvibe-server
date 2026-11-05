package vn.sunvibe.property.core.domain.model.aggregate;

import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;

/**
 * What a completed intake gives a new property (property-create intake spec 4.8, A-3, A-4): every value comes from the
 * source revision and the proof, so nothing here is a legacy listing column. {@code addressNorm} is the normalized
 * address the duplicate signal compares (A-8).
 */
public record IntakeProperty(PropertyCode code, String title, Location location, AssetType assetType,
                             RentalLayout rentalLayout, String intakeId, String wardCode, String addressNorm,
                             String ownerId, String createdBy) {

    /** It holds an address: never print it. */
    @Override
    public String toString() {
        return "IntakeProperty[" + code + "]";
    }
}
