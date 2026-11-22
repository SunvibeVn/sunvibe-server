package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The difference of a revision from the live revision (listing-publish US-4 AC-2, BR-7; E06-S6 spec section 4.3,
 * E06S6-D12): which case holds, the live revision when another one is live, and the parts that differ by value.
 */
@Schema(name = "ListingLiveDifference")
public record ListingLiveDifferenceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int32", description = "The revision asked")
        int revisionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"COMPARED", "NO_LIVE_REVISION",
                "IS_LIVE_REVISION"}, description = "COMPARED when another revision is live")
        String comparison,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "The live revision; null unless comparison is COMPARED")
        ListingLiveRevisionResponse live,
        @ArraySchema(schema = @Schema(allowableValues = {"TITLE", "DESCRIPTION", "FEATURES", "TERMS"}),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The parts of the"
                        + " asked revision that differ from the live one by value, in this order; TERMS when the"
                        + " values of the two snapshots differ, not their source or capture time; empty unless"
                        + " comparison is COMPARED"))
        List<String> changedParts) {
}
