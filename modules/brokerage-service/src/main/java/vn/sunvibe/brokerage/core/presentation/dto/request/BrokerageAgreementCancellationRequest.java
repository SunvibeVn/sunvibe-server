package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The basis lead's assigned Sale cancels an agreement that never took effect (brokerage-agreement BR-5, BR-16, US-1
 * AC-5; E05-S8 spec 3.4, 4), with the data version they read. No reason.
 */
@Schema(name = "BrokerageAgreementCancellationRequest")
public record BrokerageAgreementCancellationRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion) {
}
