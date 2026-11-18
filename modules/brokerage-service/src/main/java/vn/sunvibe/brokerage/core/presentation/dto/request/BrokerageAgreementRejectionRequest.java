package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The owner declines the version waiting for them (brokerage-agreement US-2 AC-3; E05-S5 spec S5-D7, 4). No password.
 */
@Schema(name = "BrokerageAgreementRejectionRequest")
public record BrokerageAgreementRejectionRequest(
        @NotNull @Min(1) @Max(10000)
        @Schema(description = "The version number the owner read")
        Integer versionNo,
        @Size(max = 500)
        @Schema(nullable = true, description = "Optional and private; trimmed, and stored only when not blank; no control"
                + " or bidirectional formatting character", example = "The rent is too low.")
        String reason) {

    /** brokerage-agreement BR-17: the reason stays out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementRejectionRequest[" + versionNo + "]";
    }
}
