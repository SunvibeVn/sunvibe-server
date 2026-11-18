package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * How the fee, or the renewal's fee, is set (brokerage-agreement BR-7; E05-S4 spec S4-D3, 4): PERCENT_OF_RENT with 1 to
 * 5 tiers, FIXED_AMOUNT with an amount in whole VND and its currency, FREE with a reason, NOT_CHARGED with nothing. The
 * fee takes PERCENT_OF_RENT, FIXED_AMOUNT and FREE; the renewal takes NOT_CHARGED, PERCENT_OF_RENT and FIXED_AMOUNT; a
 * type outside its place is refused with the place's allowed values.
 */
@Schema(name = "BrokerageAgreementFeeFormulaInput")
public record BrokerageAgreementFeeFormulaInput(
        @NotNull @Size(max = 20)
        @Schema(allowableValues = {"PERCENT_OF_RENT", "FIXED_AMOUNT", "FREE", "NOT_CHARGED"}, maxLength = 20,
                description = "formula takes PERCENT_OF_RENT, FIXED_AMOUNT, FREE; renewal takes NOT_CHARGED,"
                        + " PERCENT_OF_RENT, FIXED_AMOUNT")
        String type,
        @Size(max = 5)
        @Schema(nullable = true, description = "For PERCENT_OF_RENT: 1 to 5 tiers, the first from 1 month, the starts"
                + " strictly increasing; else null or []")
        List<@NotNull @Valid BrokerageAgreementFeeTierInput> tiers,
        @Min(1) @Max(999_999_999_999_999L)
        @Schema(nullable = true, format = "int64", minimum = "1", maximum = "999999999999999",
                description = "For FIXED_AMOUNT: whole VND")
        Long amount,
        @Pattern(regexp = "VND")
        @Schema(nullable = true, allowableValues = {"VND"}, description = "VND with an amount")
        String currency,
        @Size(max = 500)
        @Schema(nullable = true, maxLength = 500, description = "For FREE: why; 1 to 500 characters after trimming,"
                + " no control or bidirectional formatting character")
        String freeReason) {
}
