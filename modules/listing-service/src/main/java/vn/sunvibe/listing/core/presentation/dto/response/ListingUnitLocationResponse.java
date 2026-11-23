package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The real location of a revision's unit, opened by an eligible reviewer with an access log row (listing-publish US-4
 * AC-2; E06-S4 spec section 4.5, E06S4-D12). Private: its text names no value.
 */
@Schema(name = "ListingUnitLocation")
public record ListingUnitLocationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String unitCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The unit's location inside the property")
        String locationLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "The property's street")
        String street,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String wardName) {

    @Override
    public String toString() {
        return "ListingUnitLocationResponse[hidden]";
    }
}
