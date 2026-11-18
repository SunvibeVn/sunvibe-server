package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A monthly rent in whole VND (ADR-006 price shape). */
@Schema(name = "BrokerageAgreementRent")
public record BrokerageAgreementRentResponse(
        @Schema(format = "int64")
        long amount,
        @Schema(allowableValues = {"VND"})
        String currency,
        @Schema(allowableValues = {"MONTH"})
        String unit) {
}
