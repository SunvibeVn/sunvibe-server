package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** The live revision of a listing (listing-publish US-2 AC-7; E06-S6 spec section 4.2): whether it may be shown now. */
@Schema(name = "ListingPublicationLive")
public record ListingPublicationLiveResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant wentLiveAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The conditions of a shown revision: the listing must be Published")
        ListingDisplayAnswerResponse display) {
}
