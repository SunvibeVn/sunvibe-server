package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A listing on its staff page (listing-publish E06-S1 spec section 4, E06S1-D5, E06S1-D17): nothing of E05's answer,
 * no person but the viewer's own flag. One nullable field: {@code openRevisionNo} (E06-S2 spec E06S2-D20).
 */
@Schema(name = "ListingDetail")
public record ListingDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The internal id (staff only)")
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The public code")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UNPUBLISHED", "PUBLISHED", "PAUSED",
                "URGENTLY_UNPUBLISHED", "ARCHIVED"}, description = "ARCHIVED also when the listing's agreement has"
                + " ended")
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"LONG_TERM_RENT"})
        String transactionType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String unitId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The listing's agreement")
        String agreementId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The data version a later command sends back")
        long dataVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The viewer holds the agreement's basis lead"
                + " now; a hint, the commands decide")
        boolean viewerIsAssignedSale,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "At most the 20"
                + " newest revisions, the newest first"))
        List<ListingRevisionItemResponse> revisions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The listing has older revisions than those"
                + " answered")
        boolean hasOlderRevisions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int32",
                description = "The number of the listing's Draft or Pending review revision; null when none is open")
        Integer openRevisionNo) {
}
