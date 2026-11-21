package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What a reviewer reads beside a revision's content (listing-publish US-4 AC-2, AC-4, AC-5; E06-S4 spec section 4.2,
 * E06S4-D11): whether the caller may decide and why not, E05's answer, the terms check, the unit, the freshness and the
 * checklist. Never the real location; E05's terms are private: its text names the ids only.
 */
@Schema(name = "ListingRevisionReview")
public record ListingRevisionReviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The listing's public code, TD-XXXX-XXXX")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DRAFT", "PENDING_REVIEW",
                "NEEDS_CHANGES", "REJECTED", "APPROVED", "LIVE", "REPLACED", "CANCELLED"})
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64",
                description = "The listing's data version a decision sends back")
        long dataVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean viewerMayReview,
        @ArraySchema(schema = @Schema(allowableValues = {"NOT_A_REVIEWER", "AUTHOR", "ASSIGNED_SALE", "NOT_PENDING",
                "LISTING_ARCHIVED"}), arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Why the caller may not decide it, in this order; empty exactly when viewerMayReview"))
        List<String> notEligibleReasons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "E05's answer for the listing's unit and agreement; null when E05 answers nothing")
        ListingReviewAdvertisingResponse advertising,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"IN_EFFECT", "PENDING_START", "NONE",
                "NO_SNAPSHOT"}, description = "Which version of the agreement the revision's terms equal")
        String termsMatch,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingReviewUnitResponse unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingReviewFreshnessResponse freshness,
        @ArraySchema(schema = @Schema(allowableValues = {"CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES",
                "TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT"}), arraySchema = @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED, description = "The checklist, version 1, in this order"))
        List<String> checklist) {

    @Override
    public String toString() {
        return "ListingRevisionReviewResponse[" + listingId + ", " + revisionNo + "]";
    }
}
