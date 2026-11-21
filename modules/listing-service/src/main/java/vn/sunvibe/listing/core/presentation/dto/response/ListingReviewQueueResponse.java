package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The review queue (listing-publish US-4 AC-1; E06-S4 spec section 4.1, E06S4-D10): at most 50 Pending review revisions,
 * oldest submission first.
 */
@Schema(name = "ListingReviewQueue")
public record ListingReviewQueueResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "At most 50, oldest submission first, then by revision id"))
        List<ListingReviewQueueItemResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "More pending revisions exist than are answered")
        boolean hasMore) {
}
