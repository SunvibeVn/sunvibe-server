package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The thirteen features of a save (listing-publish BR-3; E06-S2 spec E06S2-D5): each option feature one of its codes
 * or null when not chosen, each yes-feature true or false. An unknown code or a missing yes-feature answers
 * {@code VALIDATION_FAILED} with its field. Unpublished content: its text names nothing.
 */
@Schema(name = "ListingFeaturesInput")
public record ListingFeaturesInput(
        @Pattern(regexp = "FULL|BASIC|NONE")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                allowableValues = {"FULL", "BASIC", "NONE"}, description = "Furniture; null when not stated")
        String furnishing,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean airConditioner,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean waterHeater,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean fridge,
        @Pattern(regexp = "PRIVATE|SHARED")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"PRIVATE", "SHARED"},
                description = "Washing machine; null when not stated")
        String washingMachine,
        @Pattern(regexp = "PRIVATE|SHARED")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"PRIVATE", "SHARED"},
                description = "Kitchen; null when not stated")
        String kitchen,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean balcony,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean elevator,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean motorbikeParking,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean carParking,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean internet,
        @Pattern(regexp = "ALLOWED|NOT_ALLOWED")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                allowableValues = {"ALLOWED", "NOT_ALLOWED"}, description = "Pets; null when not stated")
        String pets,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The landlord does not live here")
        Boolean noSharedLandlord) {

    @Override
    public String toString() {
        return "ListingFeaturesInput[hidden]";
    }
}
