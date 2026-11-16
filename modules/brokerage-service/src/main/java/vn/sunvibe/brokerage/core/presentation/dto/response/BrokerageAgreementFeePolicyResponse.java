package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The brokerage fee policy of a version (brokerage-agreement BR-7; E05-S4 spec 4, S4-D10): who pays and when are fixed;
 * every other part is null until entered. It holds no staff-only value, so the owner's answer reuses it.
 */
@Schema(name = "BrokerageAgreementFeePolicy")
public record BrokerageAgreementFeePolicyResponse(
        @Schema(allowableValues = {"OWNER"}, description = "Who pays the fee: the owner")
        String payer,
        @Schema(allowableValues = {"LEASE_SIGNED"}, description = "When the fee arises: when a lease is signed")
        String chargedWhen,
        @Schema(nullable = true)
        BrokerageAgreementFeeFormulaResponse formula,
        @Schema(nullable = true, description = "Days after the lease is signed; 0 = on the signing day")
        Integer dueDays,
        @Schema(nullable = true, description = "The fee of a renewal with the same tenant")
        BrokerageAgreementFeeFormulaResponse renewal,
        @Schema(nullable = true)
        BrokerageAgreementEarlyLeaseEndResponse earlyLeaseEnd,
        @Schema(nullable = true, description = "Days after the agreement ends")
        Integer protectionDays) {
}
