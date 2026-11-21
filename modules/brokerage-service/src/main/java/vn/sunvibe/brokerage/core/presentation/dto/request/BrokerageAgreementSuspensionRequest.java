package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * A manager suspends an agreement in effect (brokerage-agreement BR-15, US-5 AC-2; E05-S8 spec 3.1, 4). No data version:
 * a stop decides on the state read under its locks. Unknown properties are ignored: the manager is the signed-in one,
 * never a body field.
 */
@Schema(name = "BrokerageAgreementSuspensionRequest")
public record BrokerageAgreementSuspensionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"COMPLAINT", "SUSPECTED_FRAUD", "DISPUTE", "NEEDS_CHECK"},
                description = "Why: a complaint, suspected fraud, a dispute or a check that is needed", example = "COMPLAINT")
        String reasonType,
        @Size(max = 500)
        @Schema(nullable = true, description = "Optional and private; at most 500 characters as sent, trimmed, and stored"
                + " only when not blank; no control or bidirectional formatting character",
                example = "Tenant reported a different price.")
        String note) {

    /** brokerage-agreement BR-17: the note stays out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementSuspensionRequest[" + reasonType + "]";
    }
}
