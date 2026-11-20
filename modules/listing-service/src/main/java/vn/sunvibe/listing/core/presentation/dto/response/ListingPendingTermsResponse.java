package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * E05's activated version waiting for its start date, as a snapshot source (listing-publish US-2 AC-2; E06-S2 spec
 * section 4). E05's private terms: its text names no value.
 */
@Schema(name = "ListingPendingTerms")
public record ListingPendingTermsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        LocalDate startDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingTermsResponse terms) {

    @Override
    public String toString() {
        return "ListingPendingTermsResponse[hidden]";
    }
}
