package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Take a Draft's terms snapshot (listing-publish US-2 AC-2, BR-4; E06-S2 spec section 4, E06S2-D6): which of E05's
 * versions to copy. No field carries an amount, a term or a fee: the terms cannot be typed.
 */
@Schema(name = "ListingTermsCapture")
public record ListingTermsCaptureRequest(
        @NotNull @PositiveOrZero
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "0",
                description = "The listing's data version the caller read (listing BR-3)")
        Long dataVersion,
        @NotNull @Pattern(regexp = "IN_EFFECT|PENDING_START")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"IN_EFFECT", "PENDING_START"},
                description = "The version in effect, or the activated version waiting for its start date")
        String source) {
}
