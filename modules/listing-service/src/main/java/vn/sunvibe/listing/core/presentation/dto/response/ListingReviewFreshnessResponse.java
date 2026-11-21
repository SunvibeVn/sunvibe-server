package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * The listing's freshness summary as the review shows it (listing-publish US-4 AC-2, BR-10; E06-S4 spec section 4.2,
 * E06S4-D11): computed as the freshness read computes it.
 */
@Schema(name = "ListingReviewFreshness")
public record ListingReviewFreshnessResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "The Vietnam date of the latest valid confirmation; null until one")
        LocalDate confirmedOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "The last day the confirmation holds; null until a valid confirmation")
        LocalDate validThrough,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The last valid day has passed")
        boolean deadlinePassed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The latest record is a valid confirmation; null before any record")
        Boolean latestValid) {
}
