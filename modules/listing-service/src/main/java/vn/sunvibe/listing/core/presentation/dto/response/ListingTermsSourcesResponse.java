package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a terms snapshot can be taken from now (listing-publish US-2 AC-2; E06-S2 spec section 4, E06S2-D14): each source
 * present only when E05 has it, both null when the agreement ended. E05's private terms: its text names no value.
 */
@Schema(name = "ListingTermsSources")
public record ListingTermsSourcesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The listing's agreement ended: no source")
        boolean ended,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The terms of E05's version in effect; null without one")
        ListingTermsResponse inEffect,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "E05's activated version waiting to start; null without one")
        ListingPendingTermsResponse pending) {

    @Override
    public String toString() {
        return "ListingTermsSourcesResponse[hidden]";
    }
}
