package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One tier of a percentage fee (brokerage-agreement BR-7; E05-S4 spec S4-D3): from a lease term of fromMonths months, a
 * percentage of one month's rent. The first tier starts at 1 and the starts rise strictly.
 */
@Schema(name = "BrokerageAgreementFeeTierInput")
public record BrokerageAgreementFeeTierInput(
        @NotNull @Min(1) @Max(120)
        @Schema(minimum = "1", maximum = "120", description = "The lease term in months the tier starts at")
        Integer fromMonths,
        @NotNull @Min(1) @Max(300)
        @Schema(minimum = "1", maximum = "300", description = "Percent of one month's rent of the signed lease")
        Integer percent) {
}
