package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The listing's unit as property answers it now (listing-publish US-4 AC-2; E06-S4 spec section 4.2, E06S4-D11): its
 * code and availability, and whether an intake marked another availability (shown, never a rule).
 */
@Schema(name = "ListingReviewUnit")
public record ListingReviewUnitResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String unitCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"AVAILABLE",
                "OCCUPIED", "MAINTENANCE", "BLOCKED"}, description = "Null when property answers no unit")
        String availability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "An intake entry of the unit lists AVAILABILITY_DIFFERS")
        boolean availabilityDiffers) {
}
