package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The deposit of a draft (brokerage-agreement BR-8): MONTHS with months 0 to 12 and no amount, or AMOUNT with an amount
 * in whole VND and its currency and no months; 0 is no deposit.
 */
@Schema(name = "BrokerageAgreementDepositInput")
public record BrokerageAgreementDepositInput(
        @NotNull @Pattern(regexp = "MONTHS|AMOUNT")
        @Schema(allowableValues = {"MONTHS", "AMOUNT"})
        String basis,
        @Min(0) @Max(12)
        @Schema(nullable = true, minimum = "0", maximum = "12")
        Integer months,
        @Min(0) @Max(999_999_999_999_999L)
        @Schema(nullable = true, format = "int64", minimum = "0", maximum = "999999999999999")
        Long amount,
        @Pattern(regexp = "VND")
        @Schema(nullable = true, allowableValues = {"VND"})
        String currency) {
}
