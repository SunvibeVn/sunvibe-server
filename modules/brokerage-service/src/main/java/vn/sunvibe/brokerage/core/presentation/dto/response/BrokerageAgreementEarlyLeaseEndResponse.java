package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** What is refunded of the fee when the lease ends early (brokerage-agreement BR-7; E05-S4 spec 4). */
@Schema(name = "BrokerageAgreementEarlyLeaseEnd")
public record BrokerageAgreementEarlyLeaseEndResponse(
        @Schema(allowableValues = {"NO_REFUND", "PARTIAL_REFUND"})
        String type,
        @Schema(nullable = true, description = "Set for PARTIAL_REFUND: percent of the fee refunded")
        Integer refundPercent,
        @Schema(nullable = true, description = "Set for PARTIAL_REFUND: when the lease ends within this many months")
        Integer withinMonths) {
}
