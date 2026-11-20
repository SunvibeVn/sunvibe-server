package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * One revision of a listing (listing-publish E06-S2 spec section 4, E06S2-D14): its content, terms snapshot, unit
 * snapshot and submission time, the listing's data version, and the problems of a Draft. No user id. Unpublished text
 * and E05's terms: its text names none of them.
 */
@Schema(name = "ListingRevisionDetail")
public record ListingRevisionDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DRAFT", "PENDING_REVIEW",
                "NEEDS_CHANGES", "REJECTED", "APPROVED", "LIVE", "REPLACED", "CANCELLED"})
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Null when empty")
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Null when empty")
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingFeaturesResponse features,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Null until a snapshot is taken")
        ListingTermsSnapshotResponse termsSnapshot,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Taken at the submission; null before it")
        ListingUnitSnapshotResponse unitSnapshot,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Null before the submission")
        Instant submittedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64",
                description = "The listing's data version a later command sends back")
        long dataVersion,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The rules a submission would refuse now, in order; empty unless the status is DRAFT"))
        List<ListingRevisionProblemResponse> problems) {

    @Override
    public String toString() {
        return "ListingRevisionDetailResponse[hidden]";
    }
}
