package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * A revision's public data as it would be published, for the staff who read the listing (listing-publish E06-S7 spec
 * section 4.2, E06S7-D9; US-2 AC-3): the revision's number and status, the place names of its unit snapshot (a display
 * aid of the staff answer only), then the fields of {@link ListingPublicResponse}, absent where the revision holds none
 * yet. Data only: no display condition. Unpublished while not live: its text names no value.
 */
@Schema(name = "ListingPublicPreview")
public record ListingPublicPreviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DRAFT", "PENDING_REVIEW",
                "NEEDS_CHANGES", "REJECTED", "APPROVED", "LIVE", "REPLACED", "CANCELLED"})
        String revisionStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The province name of the revision's unit snapshot; null before its submission")
        String provinceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The ward name of the revision's unit snapshot; null before its submission")
        String wardName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The listing's public code")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"LONG_TERM_RENT"})
        String transactionType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"HOUSE",
                "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT"},
                description = "Null before the submission and on a legacy property")
        String propertyType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String provinceCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The place codes and names are set together, or all null")
        String wardCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int32",
                description = "Null before the submission")
        Integer bedrooms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int32",
                description = "Null before the submission")
        Integer bathrooms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "double",
                description = "Null before the submission")
        Double areaSqm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Null until a terms snapshot is taken")
        ListingTermsResponse terms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingFeaturesResponse features,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The Vietnam-time date the revision was put live; null unless it went live")
        LocalDate liveOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The Vietnam-time date of the listing's latest valid confirmation; null without one")
        LocalDate lastConfirmedOn) {

    @Override
    public String toString() {
        return "ListingPublicPreviewResponse[" + revisionNo + "]";
    }
}
