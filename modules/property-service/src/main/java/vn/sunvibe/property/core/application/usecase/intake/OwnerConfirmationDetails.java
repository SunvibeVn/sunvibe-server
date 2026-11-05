package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** What the code opens for the owner (property-create intake spec 7.3). It holds an address, so it stays out of every log. */
public record OwnerConfirmationDetails(String leadCode, String wardName,
                                       @Schema(nullable = true, allowableValues = {"HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY",
                                               "CONDOTEL", "APARTMENT"}) String assetType,
                                       String address, List<OwnerRequestSnapshot.Part> parts, Statement statement) {

    @Schema(name = "OwnerStatement")
    public record Statement(String version, String text) {
    }

    @Override
    public String toString() {
        return "OwnerConfirmationDetails[" + leadCode + "]";
    }
}
