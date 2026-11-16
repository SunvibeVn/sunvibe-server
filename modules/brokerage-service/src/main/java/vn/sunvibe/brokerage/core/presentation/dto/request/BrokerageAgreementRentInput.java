package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** The monthly rent of a draft in whole VND (brokerage-agreement BR-8, ADR-006). */
@Schema(name = "BrokerageAgreementRentInput")
public record BrokerageAgreementRentInput(
        @NotNull @Min(1) @Max(999_999_999_999_999L)
        @Schema(format = "int64", minimum = "1", maximum = "999999999999999")
        Long amount,
        @NotNull @Pattern(regexp = "VND")
        @Schema(allowableValues = {"VND"})
        String currency,
        @NotNull @Pattern(regexp = "MONTH")
        @Schema(allowableValues = {"MONTH"})
        String unit) {
}
