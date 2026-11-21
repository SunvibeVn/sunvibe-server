package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The listing just created: its id and public code; its detail is read on its own page (E06-S1 spec section 4). */
@Schema(name = "ListingCreated")
public record ListingCreatedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The internal id (staff only)")
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^TD-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$",
                description = "The public code; it never changes")
        String code) {
}
