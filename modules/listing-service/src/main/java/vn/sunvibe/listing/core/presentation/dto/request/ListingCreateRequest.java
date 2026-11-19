package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Create a listing under an agreement (listing-publish E06-S1 spec section 4, E06S1-D1). Unknown properties are ignored:
 * no field sets a unit, a transaction type, a state, a code, an actor or a time (security.md, mass assignment).
 */
public record ListingCreateRequest(
        @NotNull @Pattern(regexp = "^[0-9A-Z]{26}$")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The agreement the listing is bound to for"
                + " life; its unit and transaction type come from it")
        String agreementId) {
}
