package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One mandatory fee besides the rent (brokerage-agreement BR-8): an amount with its charge unit, included in the rent,
 * or at the provider's rate.
 */
@Schema(name = "BrokerageAgreementFee")
public record BrokerageAgreementFeeResponse(
        String name,
        @Schema(allowableValues = {"AMOUNT", "INCLUDED", "PROVIDER_TARIFF"})
        String basis,
        @Schema(nullable = true, format = "int64", description = "Set for AMOUNT")
        Long amount,
        @Schema(nullable = true, allowableValues = {"VND"}, description = "VND with an amount, else null")
        String currency,
        @Schema(nullable = true, allowableValues = {"PER_MONTH", "PER_KWH", "PER_M3", "PER_PERSON_PER_MONTH",
                "PER_VEHICLE_PER_MONTH", "ONE_OFF"}, description = "Set for AMOUNT")
        String chargeUnit) {
}
