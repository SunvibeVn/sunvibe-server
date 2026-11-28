package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One rule a Draft breaks now (listing-publish US-2 AC-3, AC-4; E06-S2 spec section 4, E06S2-D14): the part and the
 * rule, never the text that broke it; for a fee name the fee's 1-based position (E06-S7 spec 4.3, E06S7-D10).
 */
@Schema(name = "ListingRevisionProblem")
public record ListingRevisionProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"TITLE", "DESCRIPTION", "TERMS", "FEE_NAME"})
        String part,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"REQUIRED", "TOO_SHORT", "TOO_LONG",
                "PHONE_NUMBER", "EMAIL", "LINK", "SOCIAL_HANDLE", "FORBIDDEN_WORD"})
        String rule,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The fee's position in the terms snapshot, 1 to 20, for FEE_NAME; null otherwise")
        Integer position) {

    /** A problem of the title, the description or the terms: no position. */
    public ListingRevisionProblemResponse(String part, String rule) {
        this(part, rule, null);
    }
}
