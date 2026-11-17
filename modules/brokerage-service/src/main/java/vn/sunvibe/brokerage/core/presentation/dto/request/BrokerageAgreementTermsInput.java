package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The rental terms of a draft (brokerage-agreement BR-8; E05-S3 spec S3-D8), every part nullable while drafting.
 *
 * @param mandatoryFees null = not stated yet; empty = no mandatory fee besides the rent
 */
@Schema(name = "BrokerageAgreementTermsInput")
public record BrokerageAgreementTermsInput(
        @Valid @Schema(nullable = true)
        BrokerageAgreementRentInput monthlyRent,
        @Valid @Schema(nullable = true)
        BrokerageAgreementDepositInput deposit,
        @Min(1) @Max(120)
        @Schema(nullable = true, minimum = "1", maximum = "120")
        Integer minimumTermMonths,
        @Min(1) @Max(12)
        @Schema(nullable = true, minimum = "1", maximum = "12")
        Integer paymentPeriodMonths,
        @Size(max = 20)
        @Schema(nullable = true, description = "null = not stated yet; [] = no mandatory fee besides the rent")
        List<@NotNull @Valid BrokerageAgreementFeeInput> mandatoryFees) {
}
