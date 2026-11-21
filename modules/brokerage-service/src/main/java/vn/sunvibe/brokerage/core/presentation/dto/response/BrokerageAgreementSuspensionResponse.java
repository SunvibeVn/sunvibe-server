package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One suspension of the agreement (brokerage-agreement BR-15, BR-17; E05-S8 spec S8-D6, S8-D11): no user id. Staff
 * only; no note or reason in a text.
 */
@Schema(name = "BrokerageAgreementSuspension")
public record BrokerageAgreementSuspensionResponse(
        @Schema(allowableValues = {"COMPLAINT", "SUSPECTED_FRAUD", "DISPUTE", "NEEDS_CHECK"})
        String reasonType,
        @Schema(nullable = true, description = "The manager's note; private")
        String note,
        Instant suspendedAt,
        @Schema(nullable = true, description = "When it was lifted; null while it is open")
        Instant releasedAt,
        @Schema(nullable = true, description = "Why it was lifted; private; null while it is open")
        String releaseReason) {

    @Override
    public String toString() {
        return "BrokerageAgreementSuspensionResponse[" + reasonType + ", " + suspendedAt + "]";
    }
}
