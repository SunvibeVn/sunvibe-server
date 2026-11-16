package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * How the fee, or the renewal's fee, is set (brokerage-agreement BR-7; E05-S4 spec 4): every property is sent, a
 * nullable one as null; the tiers are empty unless PERCENT_OF_RENT.
 */
@Schema(name = "BrokerageAgreementFeeFormula")
public record BrokerageAgreementFeeFormulaResponse(
        @Schema(allowableValues = {"PERCENT_OF_RENT", "FIXED_AMOUNT", "FREE", "NOT_CHARGED"})
        String type,
        @Schema(description = "Empty unless PERCENT_OF_RENT")
        List<BrokerageAgreementFeeTierResponse> tiers,
        @Schema(nullable = true, format = "int64", description = "Set for FIXED_AMOUNT: whole VND")
        Long amount,
        @Schema(nullable = true, allowableValues = {"VND"}, description = "VND with an amount, else null")
        String currency,
        @Schema(nullable = true, description = "Set for FREE: why the brokerage is free of charge")
        String freeReason) {
}
