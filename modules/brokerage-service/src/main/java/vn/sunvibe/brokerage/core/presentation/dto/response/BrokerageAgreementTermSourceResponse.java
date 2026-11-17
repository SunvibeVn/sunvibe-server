package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Where one part of a survey proposal came from (E05-S3 spec S3-D12). */
@Schema(name = "BrokerageAgreementTermSource")
public record BrokerageAgreementTermSourceResponse(
        @Schema(description = "monthlyRent, deposit, minimumTermMonths, paymentPeriodMonths or mandatoryFees[i]")
        String field,
        @Schema(nullable = true, allowableValues = {"OBSERVED_ON_SITE", "OWNER_STATED", "OWNER_FORM"},
                description = "null when the survey recorded none of these")
        String source,
        @Schema(description = "The date of the survey's statement, else the day of the visit, in Vietnam time")
        LocalDate date) {
}
