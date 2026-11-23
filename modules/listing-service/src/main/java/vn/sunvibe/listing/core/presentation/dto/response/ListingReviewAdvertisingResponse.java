package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * E05's answer for a listing's unit and agreement as the review shows it (listing-publish US-4 AC-2, AC-5; E06-S4 spec
 * section 4.2, E06S4-D11): allowed or its reason groups, the end, the photo consents and the two versions' terms in the
 * schemas of the terms sources. E05's private terms: its text names no value.
 */
@Schema(name = "ListingReviewAdvertising")
public record ListingReviewAdvertisingResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean allowed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ended,
        @ArraySchema(schema = @Schema(allowableValues = {"NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED",
                "ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER"}), arraySchema = @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED, description = "E05's reason groups, in E05's order"))
        List<String> reasonGroups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date",
                description = "The agreement's end date; null without one")
        LocalDate endDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean interiorPhotos,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean exteriorPhotos,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The terms of E05's version in effect; null without one")
        ListingTermsResponse termsInEffect,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "E05's activated version waiting to start; null without one")
        ListingPendingTermsResponse pending) {

    @Override
    public String toString() {
        return "ListingReviewAdvertisingResponse[hidden]";
    }
}
