package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/** One version of a brokerage agreement (E05-S2 spec 4). */
@Schema(name = "BrokerageAgreementVersion")
public record BrokerageAgreementVersionResponse(
        int versionNo,
        @Schema(allowableValues = {"DRAFT", "AWAITING_OWNER", "OWNER_CONFIRMED", "ACTIVATED", "EFFECTIVE", "SUPERSEDED",
                "OWNER_REJECTED", "LAPSED", "ACTIVATION_REFUSED", "CANCELLED"})
        String status,
        @Schema(description = "A calendar date in Vietnam time")
        LocalDate startDate,
        @Schema(description = "A calendar date in Vietnam time")
        LocalDate endDate,
        Instant createdAt) {
}
