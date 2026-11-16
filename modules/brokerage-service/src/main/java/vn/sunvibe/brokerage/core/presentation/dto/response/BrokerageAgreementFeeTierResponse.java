package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One tier of a percentage fee: from a lease term in months, a percent of a month's rent (brokerage-agreement BR-7). */
@Schema(name = "BrokerageAgreementFeeTier")
public record BrokerageAgreementFeeTierResponse(
        @Schema(description = "The lease term in months the tier starts at")
        int fromMonths,
        @Schema(description = "Percent of one month's rent of the signed lease")
        int percent) {
}
