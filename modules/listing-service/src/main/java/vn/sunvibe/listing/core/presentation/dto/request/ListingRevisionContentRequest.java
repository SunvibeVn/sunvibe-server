package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.sunvibe.listing.core.presentation.validation.StorableListingDescription;
import vn.sunvibe.listing.core.presentation.validation.StorableListingTitle;

/**
 * A Draft's title, description and features, saved as a whole (listing-publish US-2 AC-1, AC-3, BR-2, BR-3; E06-S2
 * spec section 4, E06S2-D4): a null text is empty. The {@code @Size} bounds limit the memory a body takes before its
 * text is normalized; the rules are the storable ones of the revision. Unknown properties are ignored. Unpublished
 * content (US-10 AC-4): its text names nothing.
 */
@Schema(name = "ListingRevisionContent")
public record ListingRevisionContentRequest(
        @NotNull @PositiveOrZero
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "0",
                description = "The listing's data version the caller read (listing BR-3)")
        Long dataVersion,
        @Size(max = 360) @StorableListingTitle
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 360,
                description = "At most 90 characters (code points after NFC, trimmed) on one line, no control or"
                        + " format character; null or blank is empty")
        String title,
        @Size(max = 12000) @StorableListingDescription
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 12000,
                description = "At most 3,000 characters (code points after NFC, trimmed), line feeds allowed, no"
                        + " other control or format character; null or blank is empty")
        String description,
        @NotNull @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ListingFeaturesInput features) {

    @Override
    public String toString() {
        return "ListingRevisionContentRequest[hidden]";
    }
}
