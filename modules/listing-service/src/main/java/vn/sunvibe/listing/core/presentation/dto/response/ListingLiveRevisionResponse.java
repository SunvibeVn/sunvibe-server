package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The live revision a revision is compared with (listing-publish US-4 AC-2; E06-S6 spec section 4.3, E06S6-D12): it was
 * submitted, approved and put live, so it holds a title, a description and a terms snapshot. Unpublished text and E05's
 * terms: its text names none of them.
 */
@Schema(name = "ListingLiveRevision")
public record ListingLiveRevisionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant wentLiveAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingFeaturesResponse features,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingTermsSnapshotResponse terms) {

    @Override
    public String toString() {
        return "ListingLiveRevisionResponse[" + revisionNo + "]";
    }
}
