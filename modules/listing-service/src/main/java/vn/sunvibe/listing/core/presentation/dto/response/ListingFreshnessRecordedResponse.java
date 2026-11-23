package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a freshness record answers (listing-publish E06-S3 spec section 4): the listing's new data version, which the
 * next command sends back, and whether the contact is a valid confirmation.
 */
@Schema(name = "ListingFreshnessRecorded")
public record ListingFreshnessRecordedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64",
                description = "The listing's new data version")
        long dataVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Still available with both answers yes (listing-publish BR-9)")
        boolean valid) {
}
