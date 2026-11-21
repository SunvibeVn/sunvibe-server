package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessNote;
import vn.sunvibe.listing.core.presentation.validation.StorableFreshnessNote;

/**
 * A contact of the assigned Sale with the person letting the unit (listing-publish US-6 AC-1, BR-9, BR-17; E06-S3 spec
 * section 4, E06S3-D4, E06S3-D10): the channel, the result and, exactly for "still available", the two answers of BR-9;
 * an optional private note. No date and no actor: the server sets both. Unknown properties are ignored. Its text names
 * no note, outcome or answer.
 */
@Schema(name = "ListingFreshnessRecordRequest")
public record ListingFreshnessRecordRequest(
        @NotNull @PositiveOrZero
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "0",
                description = "The listing's data version the caller read (listing BR-3)")
        Long dataVersion,
        @NotNull @Pattern(regexp = "CALL|MESSAGE|IN_PERSON")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CALL", "MESSAGE", "IN_PERSON"})
        String channel,
        @NotNull @Pattern(regexp = "AVAILABLE|NOT_AVAILABLE|UNREACHABLE")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"AVAILABLE", "NOT_AVAILABLE", "UNREACHABLE"})
        String outcome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The person still lets the unit on the agreement's terms; set exactly for AVAILABLE")
        Boolean termsStillApply,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "No significant change to the unit since the listing's content; set exactly for"
                        + " AVAILABLE")
        Boolean unitUnchanged,
        @Size(max = FreshnessNote.EDGE_UNITS) @StorableFreshnessNote
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = FreshnessNote.EDGE_UNITS,
                description = "Private: at most 500 characters (code points after NFC, trimmed), line feeds allowed,"
                        + " no other control or format character; null or blank is none")
        String note) {

    /** listing-publish BR-9: both answers present exactly when the unit is still available (E06S3-D4). */
    @AssertTrue
    private boolean isAnswersMatchOutcome() {
        boolean answered = termsStillApply != null && unitUnchanged != null;
        boolean unanswered = termsStillApply == null && unitUnchanged == null;
        return "AVAILABLE".equals(outcome) ? answered : unanswered;
    }

    /** listing-publish BR-17: never the note, the outcome or an answer. */
    @Override
    public String toString() {
        return "ListingFreshnessRecordRequest[hidden]";
    }
}
