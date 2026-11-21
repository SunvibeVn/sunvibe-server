package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A revision command with no other field: start, submit, withdraw (listing-publish E06-S2 spec section 4,
 * E06S2-D1). Unknown properties are ignored: no field sets an actor, a status or a time (security.md).
 */
@Schema(name = "ListingRevisionCommand")
public record ListingRevisionCommandRequest(
        @NotNull @PositiveOrZero
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "0",
                description = "The listing's data version the caller read (listing BR-3)")
        Long dataVersion) {
}
