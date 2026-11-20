package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a revision command answers (listing-publish E06-S2 spec section 4): the revision it acted on, its status now and
 * the listing's new data version, which the next command sends back.
 */
@Schema(name = "ListingRevisionChange")
public record ListingRevisionChangeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DRAFT", "PENDING_REVIEW",
                "NEEDS_CHANGES", "REJECTED", "APPROVED", "LIVE", "REPLACED", "CANCELLED"})
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64",
                description = "The listing's new data version")
        long dataVersion) {
}
