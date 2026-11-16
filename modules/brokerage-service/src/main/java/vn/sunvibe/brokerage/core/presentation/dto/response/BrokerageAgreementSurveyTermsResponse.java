package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The survey proposal of a draft's terms (E05-S3 spec 4, S3-D11 to S3-D13): the parts the survey holds, each with its
 * source and date; every part null and no source when the survey holds none. A proposal, never the agreement's terms.
 */
@Schema(name = "BrokerageAgreementSurveyTerms")
public record BrokerageAgreementSurveyTermsResponse(
        BrokerageAgreementTermsResponse terms,
        @Schema(description = "One per part held: monthlyRent, deposit, minimumTermMonths, paymentPeriodMonths,"
                + " mandatoryFees[i]")
        List<BrokerageAgreementTermSourceResponse> sources) {
}
