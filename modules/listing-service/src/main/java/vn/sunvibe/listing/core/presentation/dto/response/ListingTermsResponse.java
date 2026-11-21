package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One version's rental terms (listing-publish BR-4, listing BR-7; E06-S2 spec section 4): the rent, the deposit, the
 * minimum term, the payment period and the mandatory fees in E05's order. E05's private terms: its text names no value.
 */
@Schema(name = "ListingTerms")
public record ListingTermsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingRentResponse monthlyRent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingDepositResponse deposit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32", minimum = "1", maximum = "120")
        int minimumTermMonths,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32", minimum = "1", maximum = "12")
        int paymentPeriodMonths,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "0 to 20 fees, E05's order"))
        List<ListingFeeResponse> mandatoryFees) {

    @Override
    public String toString() {
        return "ListingTermsResponse[hidden]";
    }
}
