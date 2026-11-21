package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The thirteen features of a revision (listing-publish BR-3; E06-S2 spec E06S2-D5): an option feature null when not
 * chosen, a yes-feature true when chosen. Unpublished content: its text names nothing.
 */
@Schema(name = "ListingFeatures")
public record ListingFeaturesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                allowableValues = {"FULL", "BASIC", "NONE"}, description = "Furniture; null when not stated")
        String furnishing,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean airConditioner,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean waterHeater,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean fridge,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"PRIVATE", "SHARED"},
                description = "Washing machine; null when not stated")
        String washingMachine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"PRIVATE", "SHARED"},
                description = "Kitchen; null when not stated")
        String kitchen,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean balcony,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean elevator,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean motorbikeParking,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean carParking,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean internet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                allowableValues = {"ALLOWED", "NOT_ALLOWED"}, description = "Pets; null when not stated")
        String pets,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The landlord does not live here")
        boolean noSharedLandlord) {

    @Override
    public String toString() {
        return "ListingFeaturesResponse[hidden]";
    }
}
