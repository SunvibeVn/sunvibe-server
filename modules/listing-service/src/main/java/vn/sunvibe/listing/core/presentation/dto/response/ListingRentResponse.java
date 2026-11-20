package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A monthly rent in whole VND, ADR-006's price shape (listing-publish E06-S2 spec section 4): the rent always carries
 * the month (listing BR-7). E05's private terms: its text names no value.
 */
@Schema(name = "ListingRent")
public record ListingRentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "1",
                maximum = "999999999999999")
        long amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"VND"})
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"MONTH"})
        String unit) {

    @Override
    public String toString() {
        return "ListingRentResponse[hidden]";
    }
}
