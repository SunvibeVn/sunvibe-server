package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A manager who is not the basis lead's assignee lifts the suspension (brokerage-agreement BR-15, US-5 AC-5; E05-S8 spec
 * 3.2, 4), with the data version they read.
 */
@Schema(name = "BrokerageAgreementSuspensionReleaseRequest")
public record BrokerageAgreementSuspensionReleaseRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion,
        @Size(max = 500)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Private; 1 to 500 characters after trimming,"
                + " at most 500 as sent; no control or bidirectional formatting character",
                example = "The price was checked with the owner.")
        String reason) {

    /** brokerage-agreement BR-17: the reason stays out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementSuspensionReleaseRequest[" + expectedDataVersion + "]";
    }
}
