package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * An independent manager activates the version the owner confirmed, against the signed document (brokerage-agreement
 * US-3 AC-1, AC-2, BR-11; E05-S6 spec S6-D1, S6-D3, 4). Unknown properties are ignored: the manager is the signed-in
 * one, never a body field; no copy of the document is sent.
 */
@Schema(name = "BrokerageAgreementActivationRequest")
public record BrokerageAgreementActivationRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion,
        @NotNull @Min(1) @Max(10000)
        @Schema(description = "The number of the version awaiting activation the manager compared")
        Integer versionNo,
        @NotNull @Size(min = 1, max = 40)
        @Schema(description = "The activation statement version the manager made", example = "brokerage-activation-2026-11")
        String statementVersion,
        @NotNull @Size(min = 1, max = 50)
        @Schema(description = "The signed document's number as written on it; private; 1 to 50 characters after"
                + " trimming, no control or bidirectional formatting character", example = "HDMG-2026/014")
        String documentNumber,
        @NotNull
        @Schema(description = "The date the document was signed, a calendar date in Vietnam from 2000-01-01 to today",
                example = "2026-11-16")
        LocalDate signedOn,
        @NotNull @Size(min = 1, max = 100)
        @Schema(description = "Sunvibe's signatory's name as written on the document; private; 1 to 100 characters after"
                + " trimming, no control or bidirectional formatting character", example = "Nguyen Van A")
        String sunvibeSignatoryName,
        @NotNull @Size(min = 1, max = 100)
        @Schema(description = "Sunvibe's signatory's title as written on the document; private; 1 to 100 characters"
                + " after trimming, no control or bidirectional formatting character", example = "Director")
        String sunvibeSignatoryTitle) {

    /** brokerage-agreement BR-17: the document's values stay out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementActivationRequest[" + versionNo + ", dataVersion=" + expectedDataVersion + "]";
    }
}
