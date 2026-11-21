package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What staff recorded when they terminated the agreement (brokerage-agreement BR-14, BR-17; E05-S8 spec S8-D5, S8-D11).
 * Staff only; no value of it in a text.
 */
@Schema(name = "BrokerageAgreementTermination")
public record BrokerageAgreementTerminationResponse(
        @Schema(allowableValues = {"OWNER_WITHDREW", "UNIT_LET", "SUNVIBE_TERMINATED"})
        String type,
        @Schema(nullable = true, description = "The date the owner said it or reported it; null for SUNVIBE_TERMINATED")
        LocalDate reportedOn,
        @Schema(nullable = true, allowableValues = {"PHONE_CALL", "IN_PERSON", "MESSAGE", "EMAIL", "OTHER"},
                description = "How the owner said it; OWNER_WITHDREW only")
        String channel,
        @Schema(nullable = true, allowableValues = {"YES", "NO", "UNKNOWN"},
                description = "Whether Sunvibe introduced the tenant; UNIT_LET only")
        String introducedBySunvibe,
        @Schema(nullable = true, description = "The note; private")
        String note,
        @Schema(description = "When the agreement was terminated")
        Instant terminatedAt) {

    @Override
    public String toString() {
        return "BrokerageAgreementTerminationResponse[" + type + "]";
    }
}
