package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** One listing to reconfirm (listing-publish US-6 AC-3, BR-10; E06-S3 spec section 4). */
@Schema(name = "ListingReconfirmationItem")
public record ListingReconfirmationItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The public code")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"UNPUBLISHED", "PUBLISHED", "PAUSED", "URGENTLY_UNPUBLISHED"})
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date",
                description = "The Vietnam date of the latest valid confirmation")
        LocalDate confirmedOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        LocalDate reconfirmFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        LocalDate validThrough,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The last valid day has passed")
        boolean deadlinePassed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The latest record is a valid confirmation")
        boolean latestRecordValid) {
}
