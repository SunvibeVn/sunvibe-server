package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The listings to reconfirm (listing-publish US-6 AC-3, BR-10; E06-S3 spec section 4, E06S3-D7): at most 50, oldest
 * confirmation first, then by id.
 */
@Schema(name = "ListingReconfirmations")
public record ListingReconfirmationsResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "At most 50, oldest confirmation first, then by listing id"))
        List<ListingReconfirmationItemResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "More listings may need reconfirmation than the list shows")
        boolean hasMore) {
}
