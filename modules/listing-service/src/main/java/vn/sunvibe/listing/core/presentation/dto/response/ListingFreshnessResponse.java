package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * The freshness of a listing (listing-publish US-6 AC-2, BR-10, BR-17; E06-S3 spec section 4, E06S3-D8): the latest
 * valid confirmation's date, its reconfirmation day and last valid day, the two flags and the latest record. No user id
 * and no data version. Its text names the listing only.
 */
@Schema(name = "ListingFreshness")
public record ListingFreshnessResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "The Vietnam date of the latest valid confirmation; null until one")
        LocalDate confirmedOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "From this day reconfirmation is due; null until a valid confirmation")
        LocalDate reconfirmFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "The last day the confirmation holds; null until a valid confirmation")
        LocalDate validThrough,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Reconfirmation is due; stays on until a new valid confirmation")
        boolean reconfirmationDue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The last valid day has passed")
        boolean deadlinePassed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The latest record; null before any")
        ListingFreshnessRecordResponse latest) {

    /** listing-publish BR-17: the listing id only. */
    @Override
    public String toString() {
        return "ListingFreshnessResponse[listingId=" + listingId + "]";
    }
}
