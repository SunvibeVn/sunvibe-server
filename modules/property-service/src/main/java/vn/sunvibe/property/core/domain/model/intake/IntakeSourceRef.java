package vn.sunvibe.property.core.domain.model.intake;

import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;

/**
 * What an intake keeps of its source revision (property-create intake spec 5.2 file 5): enough to bind a proof to it and to
 * run the duplicate signal with no call to survey-service. The address is normalized and private: never logged.
 */
public record IntakeSourceRef(String checklistId, String sha256, RentalLayout rentalScope, AssetType assetType,
                              String wardCode, String addressNorm, Double latitude, Double longitude) {

    @Override
    public String toString() {
        return "IntakeSourceRef[" + checklistId + "]";
    }
}
