package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One version of a brokerage agreement with its content (E05-S2 spec 4; E05-S3 spec 4, S3-D14; E05-S4 spec S4-D10) and
 * the owner's answer (E05-S5 spec S5-D13); the staff detail and the owner's read share it.
 */
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
        BrokerageAgreementTermsResponse terms,
        BrokerageAgreementFeePolicyResponse feePolicy,
        @Schema(nullable = true, description = "When the version was sent to the owner; null for a version never sent")
        Instant sentAt,
        @Schema(nullable = true, description = "Until when the owner may answer: 7 days after the sending, exclusive;"
                + " null for a version never sent")
        Instant answerBy,
        @Schema(nullable = true, description = "When the owner confirmed or declined the version; null otherwise")
        Instant ownerAnsweredAt,
        @Schema(nullable = true, description = "The owner's private reason for declining; null when none was given")
        String ownerRejectionReason) {

    /** brokerage-agreement BR-17: the owner's reason stays out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementVersionResponse[" + versionNo + ", " + status + "]";
    }
}
