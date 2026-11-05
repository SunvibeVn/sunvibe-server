package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What a request to a confirmable account keeps for the owner, who has no lead viewer (property-create intake spec 4.6 step 1).
 * It holds an address, so it stays out of every log.
 */
public record OwnerRequestSnapshot(String leadCode, String wardName,
                                   @Schema(nullable = true, allowableValues = {"HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL",
                                           "APARTMENT"}) String assetType,
                                   String address, List<Part> parts) {

    @Schema(name = "OwnerRequestPart")
    public record Part(String code, @Schema(nullable = true) String location) {

        @Override
        public String toString() {
            return "Part[" + code + "]";
        }
    }

    @Override
    public String toString() {
        return "OwnerRequestSnapshot[" + leadCode + "]";
    }
}
