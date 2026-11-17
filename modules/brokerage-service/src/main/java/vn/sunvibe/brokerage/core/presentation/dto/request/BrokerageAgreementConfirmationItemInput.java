package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One version the owner confirms (E05-S5 spec 4): its agreement and its number. */
@Schema(name = "BrokerageAgreementConfirmationItemInput")
public record BrokerageAgreementConfirmationItemInput(
        @NotNull @Size(max = 26)
        @Schema(description = "The agreement's id", example = "01JAGREEMENT00000000000000")
        String agreementId,
        @NotNull @Min(1) @Max(10000)
        @Schema(description = "The version number the owner read")
        Integer versionNo) {
}
