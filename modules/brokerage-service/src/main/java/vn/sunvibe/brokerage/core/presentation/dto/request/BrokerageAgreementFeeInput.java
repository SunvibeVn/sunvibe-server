package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One mandatory fee of a draft (brokerage-agreement BR-8): AMOUNT with an amount of at least 1 VND, its currency and a
 * charge unit; INCLUDED or PROVIDER_TARIFF with neither. The name is trimmed and holds no control character.
 */
@Schema(name = "BrokerageAgreementFeeInput")
public record BrokerageAgreementFeeInput(
        @NotNull @Size(min = 1, max = 100)
        @Schema(minLength = 1, maxLength = 100)
        String name,
        @NotNull @Pattern(regexp = "AMOUNT|INCLUDED|PROVIDER_TARIFF")
        @Schema(allowableValues = {"AMOUNT", "INCLUDED", "PROVIDER_TARIFF"})
        String basis,
        @Min(1) @Max(999_999_999_999_999L)
        @Schema(nullable = true, format = "int64", minimum = "1", maximum = "999999999999999")
        Long amount,
        @Pattern(regexp = "VND")
        @Schema(nullable = true, allowableValues = {"VND"})
        String currency,
        @Pattern(regexp = "PER_MONTH|PER_KWH|PER_M3|PER_PERSON_PER_MONTH|PER_VEHICLE_PER_MONTH|ONE_OFF")
        @Schema(nullable = true, allowableValues = {"PER_MONTH", "PER_KWH", "PER_M3", "PER_PERSON_PER_MONTH",
                "PER_VEHICLE_PER_MONTH", "ONE_OFF"})
        String chargeUnit) {
}
