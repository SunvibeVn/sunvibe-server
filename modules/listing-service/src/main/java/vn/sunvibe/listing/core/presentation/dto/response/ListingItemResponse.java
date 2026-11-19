package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One listing of an agreement in its listing section. */
@Schema(name = "ListingItem")
public record ListingItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The public code")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UNPUBLISHED", "PUBLISHED", "PAUSED",
                "URGENTLY_UNPUBLISHED", "ARCHIVED"}, description = "ARCHIVED also when the agreement has ended")
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {
}
