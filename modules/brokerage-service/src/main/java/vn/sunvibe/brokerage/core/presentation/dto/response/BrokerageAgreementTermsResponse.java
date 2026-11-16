package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The rental terms of a version or of a survey proposal (brokerage-agreement BR-8; E05-S3 spec 4, S3-D14), every part
 * null until entered. It holds no staff-only value, so the owner's answer reuses it.
 */
@Schema(name = "BrokerageAgreementTerms")
public record BrokerageAgreementTermsResponse(
        @Schema(nullable = true)
        BrokerageAgreementRentResponse monthlyRent,
        @Schema(nullable = true)
        BrokerageAgreementDepositResponse deposit,
        @Schema(nullable = true)
        Integer minimumTermMonths,
        @Schema(nullable = true)
        Integer paymentPeriodMonths,
        @Schema(nullable = true, description = "null = not stated yet; [] = no mandatory fee besides the rent")
        List<BrokerageAgreementFeeResponse> mandatoryFees) {
}
