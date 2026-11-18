package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What is refunded of the fee when the lease ends early (brokerage-agreement BR-7; E05-S4 spec S4-D3): NO_REFUND with
 * nothing else, or PARTIAL_REFUND with a percentage of 1 to 100 when the lease ends within 1 to 12 months.
 */
@Schema(name = "BrokerageAgreementEarlyLeaseEndInput")
public record BrokerageAgreementEarlyLeaseEndInput(
        @NotNull @Size(max = 20)
        @Schema(allowableValues = {"NO_REFUND", "PARTIAL_REFUND"}, maxLength = 20)
        String type,
        @Min(1) @Max(100)
        @Schema(nullable = true, minimum = "1", maximum = "100", description = "For PARTIAL_REFUND")
        Integer refundPercent,
        @Min(1) @Max(12)
        @Schema(nullable = true, minimum = "1", maximum = "12", description = "For PARTIAL_REFUND")
        Integer withinMonths) {
}
