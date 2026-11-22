package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The publication of a listing (listing-publish US-2 AC-7, US-5 AC-3, BR-11; E06-S6 spec section 4.2, E06S6-D11): the
 * live revision and the Approved revision waiting, each with its display answer, and what the caller may do. A hint: the
 * command decides. A listing read as archived has neither revision and permits nothing.
 */
@Schema(name = "ListingPublication")
public record ListingPublicationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UNPUBLISHED", "PUBLISHED", "PAUSED",
                "URGENTLY_UNPUBLISHED", "ARCHIVED"}, description = "ARCHIVED also when the listing's agreement has"
                + " ended")
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64",
                description = "The listing's data version a put live sends back")
        long dataVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The live revision; null when none is live or the listing is archived")
        ListingPublicationLiveResponse live,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The Approved revision waiting; null when none waits or the listing is archived")
        ListingPublicationApprovedResponse approved,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The caller is the assigned Sale, a revision"
                + " is Approved and the listing is Unpublished or Published")
        boolean viewerMayPutLive,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The caller is the assigned Sale, no Draft"
                + " or Pending review revision is open and the listing is not archived")
        boolean viewerMayCopy) {
}
