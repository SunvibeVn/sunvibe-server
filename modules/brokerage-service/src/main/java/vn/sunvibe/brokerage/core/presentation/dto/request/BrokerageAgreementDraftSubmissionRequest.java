package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Send the agreement's draft to the owner (E05-S4 spec S4-D2, 4) against the data version the caller read. Unknown
 * properties are ignored: no field sets a status, an owner, a version number or a time.
 */
@Schema(name = "BrokerageAgreementDraftSubmissionRequest")
public record BrokerageAgreementDraftSubmissionRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion) {
}
