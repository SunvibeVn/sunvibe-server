package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One rule a Draft breaks now (listing-publish US-2 AC-3, AC-4; E06-S2 spec section 4, E06S2-D14): the part and the
 * rule, never the text that broke it.
 */
@Schema(name = "ListingRevisionProblem")
public record ListingRevisionProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"TITLE", "DESCRIPTION", "TERMS"})
        String part,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"REQUIRED", "TOO_SHORT", "TOO_LONG",
                "PHONE_NUMBER", "EMAIL", "LINK", "SOCIAL_HANDLE", "FORBIDDEN_WORD"})
        String rule) {
}
