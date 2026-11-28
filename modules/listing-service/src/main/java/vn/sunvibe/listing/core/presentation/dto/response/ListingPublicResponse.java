package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * The anonymous answer about a listing (listing-publish E06-S7 spec section 4.1, E06S7-D6; listing-publish BR-14 without
 * photos; ADR-012 decision 1): exactly the public columns of its publication row and the date of its latest valid
 * confirmation (KT-3). The terms and the features in the staff schemas, which hold public data only. Never an id, the
 * digest, a state, a reason or a time of day; {@code ListingPublicResponseFieldsTest} holds the list.
 */
@Schema(name = "ListingPublic")
public record ListingPublicResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The public code, TD- and two groups of four")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"LONG_TERM_RENT"})
        String transactionType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"HOUSE",
                "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT"}, description = "Null on a legacy property")
        String propertyType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String provinceCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The province and ward codes are set together from the catalog, or both null")
        String wardCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int bedrooms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int bathrooms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "double")
        double areaSqm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingTermsResponse terms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingFeaturesResponse features,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The Vietnam-time date the shown version was put live (listing BR-6 group 9)")
        LocalDate liveOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The Vietnam-time date of the latest valid confirmation of the information")
        LocalDate lastConfirmedOn) {

    @Override
    public String toString() {
        return "ListingPublicResponse[hidden]";
    }
}
