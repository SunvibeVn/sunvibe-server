package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The unit and property fields a revision kept at its submission (listing-publish BR-4; E06-S2 spec section 4,
 * E06S2-D12): the ward and province from the catalog, all four set together or all null. Unpublished: its text names no
 * value.
 */
@Schema(name = "ListingUnitSnapshot")
public record ListingUnitSnapshotResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"HOUSE",
                "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT"}, description = "Null on a legacy property")
        String assetType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String provinceCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String provinceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String wardCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The four place fields are set together from the catalog, or all null")
        String wardName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int bedrooms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32")
        int bathrooms,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "double")
        double areaSqm) {

    @Override
    public String toString() {
        return "ListingUnitSnapshotResponse[hidden]";
    }
}
