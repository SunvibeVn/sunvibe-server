package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * One revision waiting for review (listing-publish US-4 AC-1; E06-S4 spec section 4.1): the unit snapshot taken at its
 * submission and whether the caller may review it, with the reasons why not. Unpublished text: its text names the ids
 * only.
 */
@Schema(name = "ListingReviewQueueItem")
public record ListingReviewQueueItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The public code, TD-XXXX-XXXX")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant submittedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The unit and property fields taken at the"
                + " submission")
        ListingUnitSnapshotResponse unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean viewerMayReview,
        @ArraySchema(schema = @Schema(allowableValues = {"NOT_A_REVIEWER", "AUTHOR", "ASSIGNED_SALE"}),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Why the caller may"
                        + " not review it, in this order; empty exactly when viewerMayReview"))
        List<String> notEligibleReasons) {

    @Override
    public String toString() {
        return "ListingReviewQueueItemResponse[" + listingId + ", " + revisionNo + "]";
    }
}
