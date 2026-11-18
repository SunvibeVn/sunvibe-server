package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Open the next draft version after the owner's rejection or a lapse (E05-S5 spec S5-D10, 4), against the data version
 * the caller read. Unknown properties are ignored: no field sets a content, a status, a version number or a time.
 */
@Schema(name = "BrokerageAgreementNewDraftRequest")
public record BrokerageAgreementNewDraftRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion) {
}
