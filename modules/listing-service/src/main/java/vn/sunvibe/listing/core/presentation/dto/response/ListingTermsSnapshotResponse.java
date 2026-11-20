package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A revision's terms snapshot (listing-publish US-2 AC-2, BR-4; E06-S2 spec section 4): which of E05's versions it
 * copied, that version's start date when it was waiting to start, when it was taken, and the terms. E05's private
 * terms: its text names no value.
 */
@Schema(name = "ListingTermsSnapshot")
public record ListingTermsSnapshotResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"IN_EFFECT", "PENDING_START"})
        String source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "Set exactly for PENDING_START")
        LocalDate pendingStartDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant capturedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingTermsResponse terms) {

    @Override
    public String toString() {
        return "ListingTermsSnapshotResponse[hidden]";
    }
}
