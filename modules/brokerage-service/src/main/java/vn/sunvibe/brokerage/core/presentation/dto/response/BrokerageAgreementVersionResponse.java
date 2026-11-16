package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/** One version of a brokerage agreement with its content (E05-S2 spec 4; E05-S3 spec 4, S3-D14). */
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
        Instant createdAt,
        @Schema(allowableValues = {"SUNVIBE_CHANNELS"}, description = "The advertising permission: sunvibe.vn and staff"
                + " sending the listing link, for the version's period (brokerage-agreement BR-9)")
        String advertisingScope,
        @Schema(nullable = true, description = "Public photos of the interior and common areas; null = not answered yet")
        Boolean photoConsent,
        @Schema(description = "Public photos of the exterior; true only with photoConsent true")
        boolean exteriorPhotoConsent,
        BrokerageAgreementTermsResponse terms) {
}
