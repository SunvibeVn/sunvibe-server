package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A manager's activation decision on a version (brokerage-agreement US-3 AC-2, AC-3, BR-11; E05-S6 spec S6-D14, S6-D21):
 * the document's values for an activation, the type and note for a refusal; no user id. Staff data, private
 * (brokerage-agreement BR-17): the owner's read never fills it.
 */
@Schema(name = "BrokerageAgreementVersionActivation")
public record BrokerageAgreementVersionActivationResponse(
        @Schema(allowableValues = {"ACTIVATED", "REFUSED"})
        String decision,
        Instant decidedAt,
        @Schema(nullable = true, description = "The signed document's number; set for ACTIVATED")
        String documentNumber,
        @Schema(nullable = true, description = "The signing date, a calendar date in Vietnam time; set for ACTIVATED")
        LocalDate signedOn,
        @Schema(nullable = true, description = "Sunvibe's signatory's name as written; set for ACTIVATED")
        String sunvibeSignatoryName,
        @Schema(nullable = true, description = "Sunvibe's signatory's title as written; set for ACTIVATED")
        String sunvibeSignatoryTitle,
        @Schema(nullable = true, allowableValues = {"DOCUMENT_DIFFERS", "SIGNER_NOT_OWNER"},
                description = "Why the activation was refused; set for REFUSED")
        String refusalType,
        @Schema(nullable = true, description = "The manager's note; set for REFUSED when one was given")
        String refusalNote) {

    /** brokerage-agreement BR-17: the document's values and the note stay out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementVersionActivationResponse[" + decision + "]";
    }
}
