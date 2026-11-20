package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One mandatory fee in E05's flat form (listing-publish E06-S2 spec section 4): an amount with its charge unit, included
 * in the rent, or at the provider's rate; the name is E05's content. E05's private terms: its text names no value.
 */
@Schema(name = "ListingFee")
public record ListingFeeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1 to 100 characters, E05's content")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AMOUNT", "INCLUDED", "PROVIDER_TARIFF"})
        String basis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int64",
                description = "Set for AMOUNT, at least 1")
        Long amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"VND"},
                description = "VND exactly when an amount is set")
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"PER_MONTH", "PER_KWH",
                "PER_M3", "PER_PERSON_PER_MONTH", "PER_VEHICLE_PER_MONTH", "ONE_OFF"}, description = "Set for AMOUNT")
        String chargeUnit) {

    @Override
    public String toString() {
        return "ListingFeeResponse[hidden]";
    }
}
