package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The listing section of an agreement's page (listing-publish E06-S1 spec section 4, E06S1-D1, E06S1-D17): its listings
 * and whether the viewer may create one. No field is nullable.
 */
@Schema(name = "AgreementListings")
public record AgreementListingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String agreementId,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "At most the 20"
                + " newest listings of the agreement, the newest first; at most one is not archived"))
        List<ListingItemResponse> listings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The agreement has older listings than those"
                + " answered")
        boolean hasOlder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingCreationResponse creation) {
}
