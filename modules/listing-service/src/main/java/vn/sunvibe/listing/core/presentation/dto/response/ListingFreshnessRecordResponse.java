package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The latest freshness record of a listing (listing-publish US-6 AC-2, BR-17; E06-S3 spec section 4, E06S3-D8): with its
 * private note, without the recorder. Its text names no note, outcome or answer.
 */
@Schema(name = "ListingFreshnessRecord")
public record ListingFreshnessRecordResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date",
                description = "The Vietnam date of the contact")
        LocalDate contactDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant recordedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CALL", "MESSAGE", "IN_PERSON"})
        String channel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"AVAILABLE", "NOT_AVAILABLE", "UNREACHABLE"})
        String outcome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Set exactly for AVAILABLE")
        Boolean termsStillApply,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Set exactly for AVAILABLE")
        Boolean unitUnchanged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Still available with both answers yes (listing-publish BR-9)")
        boolean valid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Private; null for none")
        String note) {

    /** listing-publish BR-17: never the note, the outcome or an answer. */
    @Override
    public String toString() {
        return "ListingFreshnessRecordResponse[hidden]";
    }
}
