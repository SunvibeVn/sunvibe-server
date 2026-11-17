package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A deposit: a number of months' rent, or an amount in whole VND; 0 is no deposit (brokerage-agreement BR-8). */
@Schema(name = "BrokerageAgreementDeposit")
public record BrokerageAgreementDepositResponse(
        @Schema(allowableValues = {"MONTHS", "AMOUNT"})
        String basis,
        @Schema(nullable = true, description = "Set for MONTHS")
        Integer months,
        @Schema(nullable = true, format = "int64", description = "Set for AMOUNT")
        Long amount,
        @Schema(nullable = true, allowableValues = {"VND"}, description = "VND with an amount, else null")
        String currency) {
}
