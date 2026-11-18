package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An independent manager refuses to activate the version the owner confirmed (brokerage-agreement US-3 AC-3, E-7; E05-S6
 * spec S6-D4, 4). Unknown properties are ignored: the manager is the signed-in one, never a body field.
 */
@Schema(name = "BrokerageAgreementActivationRefusalRequest")
public record BrokerageAgreementActivationRefusalRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion,
        @NotNull @Min(1) @Max(10000)
        @Schema(description = "The number of the version awaiting activation")
        Integer versionNo,
        @NotNull
        @Schema(allowableValues = {"DOCUMENT_DIFFERS", "SIGNER_NOT_OWNER"}, description = "DOCUMENT_DIFFERS: the signed"
                + " document differs from the version; SIGNER_NOT_OWNER: the owner's side was not signed by the owner who"
                + " confirmed it")
        String reasonType,
        @Size(max = 500)
        @Schema(nullable = true, description = "Optional and private; trimmed, and stored only when not blank; no control"
                + " or bidirectional formatting character", example = "The rent differs.")
        String note) {

    /** brokerage-agreement BR-17: the note stays out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementActivationRefusalRequest[" + versionNo + ", " + reasonType + "]";
    }
}
