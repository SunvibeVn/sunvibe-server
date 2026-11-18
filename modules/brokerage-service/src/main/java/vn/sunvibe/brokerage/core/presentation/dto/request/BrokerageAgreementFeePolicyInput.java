package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The brokerage fee policy of a draft (brokerage-agreement BR-7; E05-S4 spec S4-D1, S4-D3, 4), every part nullable while
 * drafting. Who pays (the owner) and when the fee arises (when a lease is signed) are fixed, so not part of the request.
 */
@Schema(name = "BrokerageAgreementFeePolicyInput")
public record BrokerageAgreementFeePolicyInput(
        @Valid @Schema(nullable = true)
        BrokerageAgreementFeeFormulaInput formula,
        @Min(0) @Max(30)
        @Schema(nullable = true, minimum = "0", maximum = "30", description = "Days after the lease is signed; 0 = on the"
                + " signing day")
        Integer dueDays,
        @Valid @Schema(nullable = true)
        BrokerageAgreementFeeFormulaInput renewal,
        @Valid @Schema(nullable = true)
        BrokerageAgreementEarlyLeaseEndInput earlyLeaseEnd,
        @Min(0) @Max(180)
        @Schema(nullable = true, minimum = "0", maximum = "180", description = "Days after the agreement ends")
        Integer protectionDays) {
}
