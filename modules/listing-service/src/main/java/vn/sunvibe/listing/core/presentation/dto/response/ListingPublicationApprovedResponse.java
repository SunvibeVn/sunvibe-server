package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Approved revision waiting to go live (listing-publish US-5 AC-3; E06-S6 spec section 4.2): its terms source and
 * whether it may go live now.
 */
@Schema(name = "ListingPublicationApproved")
public record ListingPublicationApprovedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"IN_EFFECT", "PENDING_START"},
                description = "Which version of the agreement its terms snapshot copied")
        String termsSource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The conditions of a put live: the listing must be Unpublished or Published")
        ListingDisplayAnswerResponse display) {
}
