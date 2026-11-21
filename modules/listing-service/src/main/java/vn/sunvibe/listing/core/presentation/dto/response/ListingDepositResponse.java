package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A deposit in E05's flat form (listing-publish E06-S2 spec section 4): a number of months' rent, or an amount in whole
 * VND; 0 is no deposit (brokerage-agreement BR-8). E05's private terms: its text names no value.
 */
@Schema(name = "ListingDeposit")
public record ListingDepositResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"MONTHS", "AMOUNT"})
        String basis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int32",
                description = "Set for MONTHS, 0 to 12")
        Integer months,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int64",
                description = "Set for AMOUNT, from 0")
        Long amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"VND"},
                description = "VND exactly when an amount is set")
        String currency) {

    @Override
    public String toString() {
        return "ListingDepositResponse[hidden]";
    }
}
