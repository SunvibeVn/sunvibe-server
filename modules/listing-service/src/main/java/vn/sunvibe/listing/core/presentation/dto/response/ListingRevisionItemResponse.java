package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One revision of a listing: its number, stored status and creation time; no creator, editor or reviewer. */
@Schema(name = "ListingRevisionItem")
public record ListingRevisionItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "From 1")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DRAFT", "PENDING_REVIEW",
                "NEEDS_CHANGES", "REJECTED", "APPROVED", "LIVE", "REPLACED", "CANCELLED"})
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {
}
